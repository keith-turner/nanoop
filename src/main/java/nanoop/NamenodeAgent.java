package nanoop;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.RawMatcher;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

public class NamenodeAgent {

  private static final String CL = "org.apache.hadoop.hdfs.protocol.ClientProtocol";
  // private static final String CL = "cmd.Goober";

  private static TypeDescription findClientProtocol(TypeDescription typeDescription) {
    for (TypeDescription.Generic generic : typeDescription.getInterfaces()) {
      if (generic.getActualName().equals(CL)) {
        return generic.asErasure();
      }
    }

    return null;
  }

  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
    System.out.printf("Starting %s\n", NamenodeAgent.class.getSimpleName());

    RawMatcher matcher = new RawMatcher() {
      @Override
      public boolean matches(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, Class<?> classBeingRedefined,
          ProtectionDomain protectionDomain) {

        if (findClientProtocol(typeDescription) != null && !typeDescription.getActualName().startsWith("com.sun.proxy")) {
          System.out.println("Found subclass " + typeDescription.getActualName());
          return true;
        }

        return false;
      }

    };

    Transformer transformer = new Transformer() {
      @Override
      public Builder<?> transform(Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule module) {
        TypeDescription clientProtoType = findClientProtocol(typeDescription);
        return builder.visit(Advice.to(NamenodeAgent.class).on(ElementMatchers.isMethod().and(ElementMatchers.isOverriddenFrom(clientProtoType))));
      }

    };

    new AgentBuilder.Default().type(matcher).transform(transformer)
        // .with(AgentBuilder.Listener.StreamWriting.toSystemOut())
        .with(AgentBuilder.TypeStrategy.Default.REDEFINE).installOn(inst);

    Thread sd = new Thread(() -> callStats.forEach((k, v) -> System.out.println(v + "\n" + k)));
    Runtime.getRuntime().addShutdownHook(sd);
  }

  @Advice.OnMethodEnter
  public static long enter() {
    // System.out.println("Enter "+method.getDeclaringClass().getSimpleName()+"."+method.getName()+" "+Arrays.toString(args));
    // Thread.dumpStack();
    return System.nanoTime();
  }

  public static class CallInfo {
    private long count = 0;
    private long min = Long.MAX_VALUE;
    private long max = Long.MIN_VALUE;
    private long sum = 0;

    public synchronized void addTime(long t) {
      t = t / 1000000; // TODO use timeunit

      count++;
      if (t < min) {
        min = t;
      }

      if (t > max) {
        max = t;
      }

      sum += t;
    }

    @Override
    public synchronized String toString() {
      return "Count:" + count + " min:" + min + " max:" + max + " avg:" + (long) (sum / (double) count);
    }
  }

  public static Map<String,CallInfo> callStats = new ConcurrentHashMap<>();

  public static final Function<String,CallInfo> CIA = k -> new CallInfo();

  @Advice.OnMethodExit
  public static void exit(@Advice.Origin java.lang.reflect.Executable method, @Advice.Enter final long startTime) {
    System.out.println("Exit " + method.getDeclaringClass().getSimpleName() + "." + method.getName());

    long endTime = System.nanoTime();

    StackTraceElement[] st = Thread.currentThread().getStackTrace();
    StringBuilder sb = new StringBuilder();

    sb.append("\tat ");
    sb.append(st[1]);
    sb.append('\n');

    boolean found = false;

    for (StackTraceElement ste : st) {
      if (ste.getClassName().startsWith("org.apache.accumulo")) {
        sb.append("\tat ");
        sb.append(ste);
        sb.append('\n');
        found = true;
        break;
      }
    }

    if (!found) {
      sb.append("\tat ");
      sb.append(st[st.length - 1]);
      sb.append('\n');
    }

    String key = sb.toString();

    callStats.computeIfAbsent(key, CIA).addTime(endTime - startTime);
  }
}
