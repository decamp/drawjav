/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.pipe;

import bits.drawjav.StreamHandle;
import bits.drawjav.audio.*;
import bits.jav.Jav;
import bits.jav.JavException;
import bits.util.ref.Refable;
import com.google.common.eventbus.EventBus;

import java.io.IOException;


/**
 * @author decamp
 */
public class ResamplerFilter implements Filter {

    private final AudioResampler mResampler;

    private final MyIn  mInPad  = new MyIn();
    private final MyOut mOutPad = new MyOut();

    private AudioPacket mPacket;
    private Exception mException;


    public ResamplerFilter( AudioAllocator alloc ) {
        mResampler = new AudioResampler( alloc );
    }



    public AudioFormat destFormat() {
        return mResampler.destFormat();
    }


    public void open( EventBus bus ) {}


    public void close() {
        mResampler.close();
    }


    public boolean isOpen() {
        return true;
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
        mResampler.clear();
    }


    private class MyIn extends InPadAdapter<AudioPacket> {
        @Override
        public int status() {
            return mException != null ? EXCEPTION :
                   mPacket != null ? DRAIN_FILTER : OKAY;
        }

        @Override
        public int offer( AudioPacket packet ) {
            mException = null;
            if( mPacket != null ) {
                return DRAIN_FILTER;
            }

            // Check for empty packet.
            AudioFormat fmt = packet.audioFormat();
            if( fmt.sampleFormat() == Jav.AV_SAMPLE_FMT_NONE ) {
                mPacket = packet;
                packet.ref();
                return OKAY;
            }

            // Not empty. Run converter.
            AudioPacket p = null;
            try {
                p = mResampler.convert( packet );
            } catch( JavException e ) {
                mException = e;
                return EXCEPTION;
            }

            if( p == null ) {
                return UNFINISHED;
            }

            mPacket = p;
            return OKAY;
        }

        @Override
        public Exception exception() {
            Exception ret = mException;
            mException = null;
            return ret;
        }
    }


    private class MyOut extends OutPadAdapter {
        @Override
        public int status() {
            return mPacket == null ? FILL_FILTER : OKAY;
        }

        @Override
        public int poll( Refable[] out ) {
            if( mPacket == null ) {
                return FILL_FILTER;
            }
            out[0] = mPacket;
            mPacket = null;
            return OKAY;
        }

        @Override
        public void config( StreamHandle stream ) throws IOException {
            if( stream == null ) {
                return;
            }

            AudioFormat fmt = stream.audioFormat();
            if( fmt == null ) {
                throw new IllegalArgumentException( "Missing picture format." );
            }

            mResampler.destFormat( fmt );
        }
    }

}
