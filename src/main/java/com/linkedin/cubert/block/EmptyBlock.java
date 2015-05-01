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

package com.linkedin.cubert.block;

import java.io.IOException;

import org.apache.pig.data.Tuple;
import org.codehaus.jackson.JsonNode;

public class EmptyBlock implements Block
{
    private static final int EMPTY_BLOCKID = -3;
    private BlockProperties props;

    public EmptyBlock(BlockProperties props)
    {
        this.props = props;
        props.setBlockId(EMPTY_BLOCKID);
    }

    public EmptyBlock(BlockProperties props, long blockId)
    {
        this.props = props;
        props.setBlockId(blockId);
    }

    @Override
    public void configure(JsonNode json) throws IOException,
            InterruptedException
    {

    }

    @Override
    public Tuple next() throws IOException,
            InterruptedException
    {
        return null;
    }

    @Override
    public void rewind() throws IOException
    {
    }

    @Override
    public BlockProperties getProperties()
    {
        return props;
    }

}
