/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import bits.util.ref.AbstractRefable;

import java.util.logging.Logger;


/**
 * @author Philip DeCamp
 */
public class NoPoolAllocator extends AbstractRefable implements PacketAllocator<DrawPacket> {

    private static final Logger LOG = Logger.getLogger( NoPoolAllocator.class.getName() );


    private int mDefaultSampleNum;


    public NoPoolAllocator( int defaultSampleNum ) {
        mDefaultSampleNum = defaultSampleNum;
    }


    public DrawPacket alloc( StreamFormat format ) {
        return alloc( format, mDefaultSampleNum );
    }

    @Override
    public DrawPacket alloc( StreamFormat format, int size ) {
        return DrawPacket.create( null, format, size );
    }

    @Override
    protected void freeObject() {}

}

