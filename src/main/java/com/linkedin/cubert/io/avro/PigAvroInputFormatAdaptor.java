/* (c) 2014 LinkedIn Corp. All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package com.linkedin.cubert.io.avro;

import java.io.IOException;
import java.util.List;

import org.apache.avro.Schema;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.pig.piggybank.storage.avro.PigAvroInputFormat;

public class PigAvroInputFormatAdaptor extends FileInputFormat<NullWritable, Writable>
{
    private PigAvroInputFormat delegate;

    @Override
    protected boolean isSplitable(JobContext context, Path filename)
    {
        Configuration conf = context.getConfiguration();
        return !conf.getBoolean("cubert.avro.input.unsplittable", false);
    }

    @Override
    public List<InputSplit> getSplits(JobContext job) throws IOException
    {
        return getDelegate(job.getConfiguration()).getSplits(job);
    }

    @Override
    public RecordReader<NullWritable, Writable> createRecordReader(InputSplit split,
                                                                   TaskAttemptContext context) throws IOException,
            InterruptedException
    {
        return getDelegate(context.getConfiguration()).createRecordReader(split, context);
    }

    private PigAvroInputFormat getDelegate(Configuration conf)
    {
        if (delegate == null)
        {
            String schemaStr = conf.get("cubert.avro.input.schema");
            Schema schema = new Schema.Parser().parse(schemaStr);
            delegate = new PigAvroInputFormat(schema, false, null, false);
        }

        return delegate;
    }

}
