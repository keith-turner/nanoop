package nanoop;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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

  private static TypeDescription findClientProtocol(TypeDescription typeDescription) {
    for (TypeDescription.Generic generic : typeDescription.getInterfaces()) {
      if (generic.getActualName().equals(CL)) {
        return generic.asErasure();
      }
    }

    return null;
  }

  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
    // finds classes that implement interface CL
    RawMatcher matcher = new RawMatcher() {
      @Override
      public boolean matches(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, Class<?> classBeingRedefined,
          ProtectionDomain protectionDomain) {

        // exclude proxy class that DFSClient creates for retry purposes
        if (findClientProtocol(typeDescription) != null && !typeDescription.getActualName().startsWith("com.sun.proxy")) {
          System.out.println("Nanoop is instrumenting " + typeDescription.getActualName());
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

    new AgentBuilder.Default().type(matcher).transform(transformer).with(AgentBuilder.TypeStrategy.Default.REDEFINE).installOn(inst);

    Thread sd = new Thread(() -> dump());
    Runtime.getRuntime().addShutdownHook(sd);
  }

  // this code is inlined at the beginning of each NN rpc function
  @Advice.OnMethodEnter
  public static long enter() {
    return System.nanoTime();
  }

  // this code is inlined at the end of each NN rpc function
  @Advice.OnMethodExit
  public static void exit(@Advice.Enter final long startTime) {
    long endTime = System.nanoTime();

    StackTraceElement[] st = Thread.currentThread().getStackTrace();

    callStats.computeIfAbsent(createKey(st), CIA).addDuration(endTime - startTime, TimeUnit.NANOSECONDS);
  }

  public static class CallInfo {
    private long count = 0;
    private long min = Long.MAX_VALUE;
    private long max = Long.MIN_VALUE;
    private long sum = 0;
    private static final TimeUnit TIME_UNIT = TimeUnit.MICROSECONDS;

    public synchronized void addDuration(long duration, TimeUnit tu) {
      long t = TIME_UNIT.convert(duration, tu);

      count++;
      if (t < min)
        min = t;

      if (t > max)
        max = t;

      sum += t;
    }

    @Override
    public synchronized String toString() {
      return count + "|" + format(min) + "|" + format(max) + "|" + format((long) (sum / (double) count));
    }

    private static String format(long t) {
      if (TIME_UNIT != TimeUnit.MICROSECONDS)
        throw new IllegalStateException();
      return String.format("%.3f", t / 1000.0);
    }
  }

  public static Map<String,CallInfo> callStats = new ConcurrentHashMap<>();

  public static final Function<String,CallInfo> CIA = k -> new CallInfo();

  /**
   * Condense a stack trace into a key that identifies two pieces of information : the NN function AND the Accumulo code making that call.
   */
  public static String createKey(StackTraceElement[] st) {
    StringBuilder sb = new StringBuilder();

    sb.append(st[1].getMethodName());
    sb.append("|");

    // append last accumulo code call if present
    for (StackTraceElement ste : st)
      if (ste.getClassName().startsWith("org.apache.accumulo")) {
        sb.append(ste.getClassName().replace("org.apache.accumulo", "o.a.a") + "." + ste.getMethodName());
        return sb.toString();
      }

    // if no accumulo elements found, then append function that started thread
    sb.append(st[st.length - 1].getClassName() + "." + st[st.length - 1].getMethodName());

    return sb.toString();
  }

  public static void dump() {
    System.out.println("NN method|Accumulo method|Count|min time|max time|avg time");
    callStats.forEach((k, v) -> System.out.println(k + "|" + v));
  }
}
