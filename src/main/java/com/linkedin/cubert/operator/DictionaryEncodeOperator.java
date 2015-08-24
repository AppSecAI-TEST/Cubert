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

package com.linkedin.cubert.operator;

import com.linkedin.cubert.block.DataType;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;

import com.linkedin.cubert.block.Block;
import com.linkedin.cubert.block.BlockProperties;
import com.linkedin.cubert.block.BlockSchema;
import com.linkedin.cubert.block.ColumnType;
import com.linkedin.cubert.plan.physical.GenerateDictionary;
import com.linkedin.cubert.utils.CodeDictionary;
import com.linkedin.cubert.utils.FileCache;
import com.linkedin.cubert.utils.JsonUtils;

/**
 * Encodes columns in a tuple using a CodeDictionary.
 * 
 * 
 * The properties for this operator are:
 * <ul>
 * <li>dictionary (string): the name of the dictionary block</li>
 * 
 * <li>compact (boolean; optional, default:false): whether to compact the dictionary</li>
 * </ul>
 * 
 * @author Maneesh Varshney
 * 
 */
public class DictionaryEncodeOperator implements TupleOperator
{

    private CodeDictionary[] dictionaries;
    private Block dataBlock;
    private Tuple encodedTuple;
    private String replaceNull = null;
    private Integer replaceUnknownCodes = null;

    @Override
    public void setInput(Map<String, Block> input, JsonNode json, BlockProperties props) throws IOException,
            InterruptedException
    {
        // load the dictionaries
        Map<String, CodeDictionary> dictionaryMap = null;
        if (json.has("path"))
        {
            String dictionaryName = json.get("path").getTextValue();
            String dictionaryPath = FileCache.get(dictionaryName);
            dictionaryPath = dictionaryPath + "/part-r-00000.avro";

            dictionaryMap =
                    GenerateDictionary.loadDictionary(dictionaryPath, false, null);
        }
        else
        {
            // this is inline dictionary

            JsonNode dictionary = json.get("dictionary");
            Iterator<String> nameIterator = dictionary.getFieldNames();
            dictionaryMap = new HashMap<String, CodeDictionary>();
            while (nameIterator.hasNext())
            {
                String name = nameIterator.next();
                ArrayNode values = (ArrayNode) dictionary.get(name);
                CodeDictionary codeDictionary = new CodeDictionary();
                for (JsonNode value : values)
                {
                    codeDictionary.addKey(value.getTextValue());
                }
                dictionaryMap.put(name, codeDictionary);
            }
        }

        dataBlock = input.values().iterator().next();
        BlockSchema inputSchema = dataBlock.getProperties().getSchema();

        // assign the dictionaries in the array
        dictionaries = new CodeDictionary[inputSchema.getNumColumns()];

        for (int i = 0; i < dictionaries.length; i++)
        {
            String colName = inputSchema.getName(i);

            if (dictionaryMap.containsKey(colName))
            {
                dictionaries[i] = dictionaryMap.get(colName);
            }
            else
            {
                dictionaries[i] = null;
            }
        }

        // create output tuple
        encodedTuple = TupleFactory.getInstance().newTuple(inputSchema.getNumColumns());

        if (json.has("replaceNull"))
        {
            replaceNull = JsonUtils.getText(json, "replaceNull");
        }

        if (json.has("replaceUnknownCodes"))
        {
            replaceUnknownCodes = Integer.parseInt(JsonUtils.getText(json, "replaceUnknownCodes"));
        }
    }

    @Override
    public Tuple next() throws IOException,
            InterruptedException
    {
        Tuple tuple = dataBlock.next();
        if (tuple == null)
            return null;

        for (int i = 0; i < encodedTuple.size(); i++)
        {
            if (dictionaries[i] == null)
            {
                encodedTuple.set(i, tuple.get(i));
            }
            else
            {
                Object val = tuple.get(i);
                String str;
                if (val == null)
                {
                    if (replaceNull == null)
                        throw new RuntimeException("'null' cannot be encoded");

                    str = replaceNull;
                }
                else
                {
                    str = val.toString();
                }

                int code = dictionaries[i].getCodeForKey(str);
                if (code <= 0)
                {
                    if (replaceUnknownCodes == null)
                    {
                        throw new RuntimeException("String '" + str
                            + "' not found in dictionary.");
                    }
                    else
                    {
                        code = replaceUnknownCodes.intValue();
                    }
                }

                encodedTuple.set(i, code);
            }
        }

        return encodedTuple;
    }

    @Override
    public PostCondition getPostCondition(Map<String, PostCondition> preConditions,
                                          JsonNode json) throws PreconditionException
    {
        String inputBlockName = JsonUtils.getText(json, "input");
        PostCondition inputCondition = preConditions.get(inputBlockName);
        BlockSchema inputSchema = inputCondition.getSchema();

        Map<String, CodeDictionary> dictionaryMap = new HashMap<String, CodeDictionary>();

        if (json.has("columns"))
        {
            String[] columns = JsonUtils.asArray(json, "columns");
            for (String column : columns)
                dictionaryMap.put(column, new CodeDictionary());
        }
        else
        {
            JsonNode dictionary = json.get("dictionary");

            // this is inline dictionary
            Iterator<String> nameIterator = dictionary.getFieldNames();
            while (nameIterator.hasNext())
            {
                String name = nameIterator.next();
                ArrayNode values = (ArrayNode) dictionary.get(name);
                CodeDictionary codeDictionary = new CodeDictionary();
                for (JsonNode value : values)
                {
                    codeDictionary.addKey(value.getTextValue());
                }
                dictionaryMap.put(name, codeDictionary);
            }
        }

        ColumnType[] columnTypes = new ColumnType[inputSchema.getNumColumns()];

        for (int i = 0; i < columnTypes.length; i++)
        {
            ColumnType type;
            final String name = inputSchema.getName(i);

            if (dictionaryMap.containsKey(name))
            {
                // this column is encoded. Transform schema
                type = new ColumnType(name, DataType.INT);
            }
            else
            {
                // this column is not encoded. Reuse schema
                type = inputSchema.getColumnType(i);
            }

            columnTypes[i] = type;
        }

        BlockSchema schema = new BlockSchema(columnTypes);

        return new PostCondition(schema,
                                 inputCondition.getPartitionKeys(),
                                 inputCondition.getSortKeys());
    }

}
