/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.pipe;

import bits.drawjav.*;
import bits.drawjav.audio.*;
import bits.jav.JavException;
import bits.util.ref.Refable;
import com.google.common.eventbus.EventBus;

import java.io.IOException;


/**
 * @author decamp
 */
public class AudioResamplerUnit implements AvUnit {


    private final InHandler  mInPad  = new InHandler();
    private final OutHandler mOutPad = new OutHandler();

    private final MemoryManager mOptMem;

    private boolean mOpen = false;

    private StreamFormat   mDestFormat;
    private AudioResampler mResampler;
    private DrawPacket     mOutPacket;
    private Exception      mException;


    public AudioResamplerUnit( MemoryManager optMem ) {
        mOptMem = optMem;
    }


    public void open( EventBus bus ) {
        if( mOpen ) {
            return;
        }
        mOpen = true;

        AudioAllocator alloc = null;
        if( mOptMem != null ) {
            alloc = mOptMem.audioAllocator( mDestFormat );
        } else {
            alloc = OneFormatAudioAllocator.createPacketLimited( 64, -1 );
        }

        mResampler = new AudioResampler( alloc );
        mResampler.requestFormat( mDestFormat );
    }


    public void close() {
        if( !mOpen ) {
            return;
        }
        mOpen = false;
        mResampler.close();
        mResampler = null;
    }


    public boolean isOpen() {
        return mOpen;
    }

    @Override
    public int inputNum() {
        return 1;
    }

    @Override
    public InPad input( int idx ) {
        return mInPad;
    }

    @Override
    public int outputNum() {
        return 1;
    }

    @Override
    public OutPad output( int idx ) {
        return mOutPad;
    }

    @Override
    public void clear() {
        if( mResampler != null ) {
            mResampler.clear();
        }
    }


    private class InHandler extends InPadAdapter<DrawPacket> {
        @Override
        public int status() {
            return mException != null ? EXCEPTION :
                   mOutPacket != null ? DRAIN_FILTER : OKAY;
        }

        @Override
        public int offer( DrawPacket packet ) {
            mException = null;
            if( mOutPacket != null ) {
                return DRAIN_FILTER;
            }

            // Check for empty packet.
            if( packet.isGap() ) {
//                mOutPacket = packet;
//                packet.ref();
                return OKAY;
            }

            // Not empty. Run converter.
            try {
                mOutPacket = mResampler.convert( packet );
                return OKAY;
            } catch( JavException e ) {
                mException = e;
                return EXCEPTION;
            }
        }

        @Override
        public Exception exception() {
            Exception ret = mException;
            mException = null;
            return ret;
        }
    }


    private class OutHandler extends OutPadAdapter {
        @Override
        public int status() {
            return mOutPacket == null ? FILL_FILTER : OKAY;
        }

        @Override
        public int poll( Refable[] out ) {
            if( mOutPacket == null ) {
                return FILL_FILTER;
            }

            out[0] = mOutPacket;
            mOutPacket = null;
            return OKAY;
        }

        @Override
        public void config( StreamFormat format ) throws IOException {
            if( format == null ) {
                return;
            }

            mDestFormat = format;
        }
    }

}
