# Instrumented Bulk Import test

Using [bulky] 10,000 files were imported into Accumulo 1.9.2 that was
instrumented with nanoop. The table was split into 100 tablets and configured
so that compactions would not happen.  After the test concluded all servers
were sent SIGTERM.  The following command were run for the test.

```bash
hadoop fs -rm -R /bulk0*

hadoop fs -mkdir /bulk01-fail

accumulo shell -u root -p secret -e 'deletetable -f bulky'
mvn package
./bin/run.sh cmd.Split 100
accumulo shell -u root -p secret -e 'config -t bulky -s table.compaction.major.ratio=1000'
accumulo shell -u root -p secret -e 'config -t bulky -s table.file.max=1000'
./bin/run.sh cmd.Generate IA 100 10000 1000000 /bulk01
./bin/run.sh cmd.Import old /bulk01
```

The following sections show the nanoop output for each Accumulo process after
the test.  There was only a single tserver for the test. The times below are in
milliseconds.  The `Accumulo method` column is the accumulo method that called
DFS code OR the method at the bottom of the stack.

This instrumentation shows that around 40K RPCs were made to import 10K files.
The tserver made 30K RPCs and the master made 10K RPCs.

## Tablet Server

NN method|Accumulo method|Count|min time|max time|avg time
---------|---------------|-----|--------|--------|--------
addBlock|org.apache.hadoop.hdfs.DataStreamer.run|16|1.021|8.945|3.259
complete|o.a.a.core.file.streams.RateLimitedOutputStream.close|19|0.458|7.882|2.294
create|o.a.a.core.file.rfile.RFileOperations.openWriter|14|0.948|7.995|3.137
create|o.a.a.server.fs.VolumeManagerImpl.createSyncable|2|3.057|23.767|13.412
delete|o.a.a.server.fs.VolumeManagerImpl.deleteRecursively|6|0.894|6.074|2.596
fsync|o.a.a.tserver.log.DfsLogger$LogSyncingTask.run|2|4.983|5.983|5.483
getBlockLocations|o.a.a.core.file.blockfile.impl.CachableBlockFile$Reader.getBCFile|10059|0.246|71.183|1.370
getContentSummary|o.a.a.server.client.BulkImporter.estimateSizes|10000|0.200|66.054|0.784
getFileInfo|o.a.a.core.file.blockfile.impl.CachableBlockFile$Reader$1.call|10|0.279|6.441|2.172
getFileInfo|o.a.a.core.file.blockfile.impl.CachableBlockFile$Reader.getBCFile|10047|0.157|66.528|0.858
getFileInfo|o.a.a.server.fs.VolumeManagerImpl.exists|113|0.262|30.201|4.917
getListing|o.a.a.core.zookeeper.ZooUtil.getInstanceIDFromHdfs|2|2.640|156.419|79.529
getListing|o.a.a.server.Accumulo.getAccumuloPersistentVersion|2|9.128|13.769|11.448
getListing|o.a.a.server.fs.VolumeManagerImpl.globStatus|2|1.578|8.872|5.225
getListing|o.a.a.server.fs.VolumeManagerImpl.listStatus|7|0.456|67.017|14.564
getServerDefaults|o.a.a.core.file.streams.RateLimitedInputStream.read|1|16.557|16.557|16.557
mkdirs|o.a.a.server.fs.VolumeManagerImpl.mkdirs|99|1.470|25.363|5.717
rename|o.a.a.server.fs.VolumeManagerImpl.rename|20|0.835|5.719|1.826
renewLease|java.lang.Thread.run|12|0.420|15.501|3.779
setSafeMode|o.a.a.server.fs.VolumeManagerImpl.isReady|1|59.392|59.392|59.392

## Master

NN method|Accumulo method|Count|min time|max time|avg time
---------|---------------|-----|--------|--------|--------
complete|o.a.a.master.tableOps.LoadFiles.call|1|1.121|1.121|1.121
complete|o.a.a.server.fs.VolumeManagerImpl.createNewFile|1|5.978|5.978|5.978
create|o.a.a.server.fs.VolumeManagerImpl.create|1|7.714|7.714|7.714
create|o.a.a.server.fs.VolumeManagerImpl.createNewFile|1|10.190|10.190|10.190
delete|o.a.a.server.fs.VolumeManagerImpl.delete|1|5.186|5.186|5.186
delete|o.a.a.server.fs.VolumeManagerImpl.deleteRecursively|1|3.031|3.031|3.031
getBlockLocations|o.a.a.server.fs.VolumeManagerImpl.open|1|3.679|3.679|3.679
getFileInfo|o.a.a.server.fs.VolumeManagerImpl.createNewFile|1|0.610|0.610|0.610
getFileInfo|o.a.a.server.fs.VolumeManagerImpl.exists|2|0.740|0.791|0.765
getFileInfo|o.a.a.server.fs.VolumeManagerImpl.getFileStatus|1|6.366|6.366|6.366
getListing|o.a.a.core.zookeeper.ZooUtil.getInstanceIDFromHdfs|2|3.088|306.091|154.589
getListing|o.a.a.server.Accumulo.getAccumuloPersistentVersion|4|1.200|8.160|3.658
getListing|o.a.a.server.fs.VolumeManagerImpl.listStatus|21|0.962|28.214|7.288
mkdirs|o.a.a.server.fs.VolumeManagerImpl.mkdirs|3|0.825|5.501|2.584
rename|o.a.a.server.fs.VolumeManagerImpl.rename|10000|0.756|64.129|5.504
setSafeMode|o.a.a.server.fs.VolumeManagerImpl.isReady|1|69.130|69.130|69.130

## GC

NN method|Accumulo method|Count|min time|max time|avg time
---------|---------------|-----|--------|--------|--------
delete|o.a.a.server.fs.VolumeManagerImpl.deleteRecursively|10|1.814|26.879|16.456
getFileInfo|o.a.a.server.fs.VolumeManagerImpl.exists|2|1.704|6.673|4.188
getListing|o.a.a.core.zookeeper.ZooUtil.getInstanceIDFromHdfs|2|6.755|319.641|163.198
getListing|o.a.a.server.Accumulo.getAccumuloPersistentVersion|2|1.722|5.549|3.635
setSafeMode|o.a.a.server.fs.VolumeManagerImpl.isReady|1|93.803|93.803|93.803

[bulky]: https://github.com/keith-turner/bulky
