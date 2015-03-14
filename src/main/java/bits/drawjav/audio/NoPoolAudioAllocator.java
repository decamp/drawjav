/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.audio;

import bits.drawjav.*;
import bits.util.ref.AbstractRefable;

import java.util.logging.Logger;


/**
 * @author Philip DeCamp
 */
public class NoPoolAudioAllocator extends AbstractRefable implements AudioAllocator {

    private static final Logger LOG = Logger.getLogger( NoPoolAudioAllocator.class.getName() );


    private int mDefaultSampleNum;


    public NoPoolAudioAllocator( int defaultSampleNum ) {
        mDefaultSampleNum = defaultSampleNum;
    }


    public DrawPacket alloc( AudioFormat format ) {
        return alloc( format, mDefaultSampleNum );
    }

    @Override
    public DrawPacket alloc( AudioFormat format, int numSamples ) {
        if( numSamples < 0 ) {
            DrawPacket ret = DrawPacket.createAuto( null );
            ret.setAudioFormat( format );
            return ret;
        }
        return DrawPacket.createAudio( null, format, numSamples, 0 );
    }

    @Override
    protected void freeObject() {}

}

