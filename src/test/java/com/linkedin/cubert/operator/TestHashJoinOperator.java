/*
 * (c) 2014 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package com.linkedin.cubert.operator;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.data.Tuple;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.linkedin.cubert.block.Block;
import com.linkedin.cubert.block.BlockProperties;
import com.linkedin.cubert.block.BlockSchema;
import com.linkedin.cubert.block.ColumnType;
import com.linkedin.cubert.block.DataType;
import com.linkedin.cubert.block.TupleStoreBlock;
import com.linkedin.cubert.plan.physical.CubertStrings;
import com.linkedin.cubert.utils.DataGenerator;
import com.linkedin.cubert.utils.RawTupleStore;
import com.linkedin.cubert.utils.TupleStore;

/**
 * Tests the Hash JOIN operator
 *
 * Created by spyne on 10/30/14.
 */
public class TestHashJoinOperator
{
    private final int nRows = 1000;
    final DataGenerator dataGenerator = new DataGenerator();

    final String lBlockName = "lBlock";
    final String rBlockName = "rBlock";

    public static void setup(Boolean useCompactSerialization) throws IOException
    {
        final Configuration conf = new Configuration();
        conf.set(CubertStrings.USE_COMPACT_SERIALIZATION, useCompactSerialization.toString());
        PhaseContext.create((Mapper.Context) null, conf);
    }

    @Test
    public void testInnerHashJoin() throws IOException, InterruptedException
    {
        setup(false);

        BlockSchema schema = DataGenerator.createBlockSchema();
        dataGenerator.setMIN_INT(0);
        dataGenerator.setMAX_INT(1000000);
        dataGenerator.setMIN_STRING_LENGTH(5);
        dataGenerator.setMAX_STRING_LENGTH(10);

        final List<Tuple> tuples = dataGenerator.generateSequentialTuples(nRows, schema);

        /* Create the Tuple Schema */
        final BlockSchema lSchema = new BlockSchema(new ColumnType[] {
                new ColumnType("Integer", DataType.INT),
                new ColumnType("Long", DataType.LONG)
        });
        final BlockSchema rSchema = new BlockSchema(new ColumnType[] {
                new ColumnType("Integer", DataType.INT),
                new ColumnType("Double", DataType.DOUBLE),
                new ColumnType("String", DataType.STRING)
        });
        final BlockSchema operatorSchema = new BlockSchema(new ColumnType[] {
                new ColumnType(lBlockName + "___" + "Integer", DataType.INT),
                new ColumnType(lBlockName + "___" + "Long", DataType.LONG),
                new ColumnType(rBlockName + "___" + "Integer", DataType.INT),
                new ColumnType(rBlockName + "___" + "Double", DataType.DOUBLE),
                new ColumnType(rBlockName + "___" + "String", DataType.STRING)
        });

        final TupleStore lStore = new RawTupleStore(lSchema);
        final TupleStore rStore = new RawTupleStore(rSchema);

        /* For the test "Integer" field is being used as the JOIN key */
        for (Tuple t : tuples)
        {
            Tuple lt = extractFields(t, schema, lSchema);
            Tuple rt = extractFields(t, schema, rSchema);

            lStore.addToStore(lt);
            rStore.addToStore(rt);
        }
        HashJoinOperator operator = createHashJoinOperator(lSchema, rSchema, operatorSchema, lStore, rStore);

        for (Tuple t : tuples)
        {
            Tuple output = operator.next();

            Assert.assertEquals(5, output.size());

            Assert.assertEquals(t.get(0), output.get(0));
            Assert.assertEquals(t.get(0), output.get(2));
            Assert.assertEquals(t.get(1), output.get(1));
            Assert.assertEquals(t.get(2), output.get(3));
            Assert.assertEquals(t.get(3), output.get(4));
        }
        Assert.assertNull(operator.next());
    }

    @Test
    public void testInnerHashJoinRightBlockEmpty() throws IOException, InterruptedException
    {
        setup(false);

        BlockSchema schema = DataGenerator.createBlockSchema();
        dataGenerator.setMIN_INT(0);
        dataGenerator.setMAX_INT(1000000);
        dataGenerator.setMIN_STRING_LENGTH(5);
        dataGenerator.setMAX_STRING_LENGTH(10);

        final List<Tuple> tuples = dataGenerator.generateSequentialTuples(nRows, schema);

        /* Create the Tuple Schema */
        final BlockSchema lSchema = new BlockSchema(new ColumnType[] {
                new ColumnType("Integer", DataType.INT),
                new ColumnType("Long", DataType.LONG)
        });
        final BlockSchema rSchema = new BlockSchema(new ColumnType[] {
                new ColumnType("Integer", DataType.INT),
                new ColumnType("Double", DataType.DOUBLE),
                new ColumnType("String", DataType.STRING)
        });
        final BlockSchema operatorSchema = new BlockSchema(new ColumnType[] {
                new ColumnType(lBlockName + "___" + "Integer", DataType.INT),
                new ColumnType(lBlockName + "___" + "Long", DataType.LONG),
                new ColumnType(rBlockName + "___" + "Integer", DataType.INT),
                new ColumnType(rBlockName + "___" + "Double", DataType.DOUBLE),
                new ColumnType(rBlockName + "___" + "String", DataType.STRING)
        });

        final TupleStore lStore = new RawTupleStore(lSchema);
        final TupleStore rStore = new RawTupleStore(rSchema);

        /* For the test "Integer" field is being used as the JOIN key */
        for (Tuple t : tuples)
        {
            Tuple lt = extractFields(t, schema, lSchema);
            lStore.addToStore(lt);
        }
        HashJoinOperator operator = createHashJoinOperator(lSchema, rSchema, operatorSchema, lStore, rStore);

        Assert.assertNull(operator.next());
    }

    @Test
    public void testInnerHashJoinLeftBlockEmpty() throws IOException, InterruptedException
    {
        setup(false);

        BlockSchema schema = DataGenerator.createBlockSchema();
        dataGenerator.setMIN_INT(0);
        dataGenerator.setMAX_INT(1000000);
        dataGenerator.setMIN_STRING_LENGTH(5);
        dataGenerator.setMAX_STRING_LENGTH(10);

        final List<Tuple> tuples = dataGenerator.generateSequentialTuples(nRows, schema);

        /* Create the Tuple Schema */
        final BlockSchema lSchema = new BlockSchema(new ColumnType[] {
                new ColumnType("Integer", DataType.INT),
                new ColumnType("Long", DataType.LONG)
        });
        final BlockSchema rSchema = new BlockSchema(new ColumnType[] {
                new ColumnType("Integer", DataType.INT),
                new ColumnType("Double", DataType.DOUBLE),
                new ColumnType("String", DataType.STRING)
        });
        final BlockSchema operatorSchema = new BlockSchema(new ColumnType[] {
                new ColumnType(lBlockName + "___" + "Integer", DataType.INT),
                new ColumnType(lBlockName + "___" + "Long", DataType.LONG),
                new ColumnType(rBlockName + "___" + "Integer", DataType.INT),
                new ColumnType(rBlockName + "___" + "Double", DataType.DOUBLE),
                new ColumnType(rBlockName + "___" + "String", DataType.STRING)
        });

        final TupleStore lStore = new RawTupleStore(lSchema);
        final TupleStore rStore = new RawTupleStore(rSchema);

        /* For the test "Integer" field is being used as the JOIN key */
        for (Tuple t : tuples)
        {
            Tuple rt = extractFields(t, schema, rSchema);
            rStore.addToStore(rt);
        }
        HashJoinOperator operator = createHashJoinOperator(lSchema, rSchema, operatorSchema, lStore, rStore);

        Assert.assertNull(operator.next());
    }


    private Tuple extractFields(final Tuple source,
                                final BlockSchema srcSchema,
                                final BlockSchema dstSchema) throws ExecException
    {
        Tuple output = DataGenerator.newTuple(dstSchema.getNumColumns());

        String[] columnNames = dstSchema.getColumnNames();
        for (int i = 0; i < columnNames.length; i++)
        {
            output.set(i, source.get(srcSchema.getIndex(columnNames[i])));
        }

        return output;
    }

    public HashJoinOperator createHashJoinOperator(BlockSchema lSchema,
                                                   BlockSchema rSchema,
                                                   BlockSchema operatorSchema,
                                                   TupleStore lStore,
                                                   TupleStore rStore) throws IOException, InterruptedException
    {
        /* Create Blocks */
        final Block lBlock = new TupleStoreBlock(lStore, new BlockProperties(lBlockName, lSchema, (BlockProperties) null));
        final Block rBlock = new TupleStoreBlock(rStore, new BlockProperties(rBlockName, rSchema, (BlockProperties) null));

        /* Perform the Hash Join */
        Map<String, Block> input = new HashMap<String, Block>();
        input.put(lBlockName, lBlock);
        input.put(rBlockName, rBlock);

        ObjectNode root = new ObjectNode(JsonNodeFactory.instance);
        root.put("leftBlock", lBlockName);
        root.put("rightBlock", rBlockName);

        final ArrayNode joinKeys = new ArrayNode(JsonNodeFactory.instance);
        joinKeys.add("Integer");
        root.put("leftJoinKeys", joinKeys);
        root.put("rightJoinKeys", joinKeys);

        BlockProperties props = new BlockProperties("Joined", operatorSchema, (BlockProperties) null);
        HashJoinOperator operator = new HashJoinOperator();

        operator.setInput(input, root, props);
        return operator;
    }
}
