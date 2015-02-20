/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.pipe;

import bits.drawjav.audio.*;
import bits.jav.Jav;
import bits.jav.JavException;

import java.io.IOException;


/**
 * @author decamp
 */
public class ResamplerFilter implements Filter {

    private final AudioResampler mResampler;
    private final Sink        mSink  = new Sink();
    private final QueueSource mQueue = new QueueSource<AudioPacket>( 1 );


    public ResamplerFilter( AudioFormat destFormat, AudioAllocator alloc ) {
        mResampler = new AudioResampler( alloc );
        mResampler.destFormat( destFormat );
    }


    public AudioFormat destFormat() {
        return mResampler.destFormat();
    }


    public void close() throws IOException {
        mResampler.close();
    }


    public boolean isOpen() {
        return true;
    }

    @Override
    public int sinkNum() {
        return 1;
    }

    @Override
    public SinkPad sink( int idx ) {
        return mSink;
    }

    @Override
    public int sourceNum() {
        return 1;
    }

    @Override
    public SourcePad source( int idx ) {
        return mQueue;
    }

    @Override
    public void clear() {
        mResampler.clear();
    }


    private class Sink implements SinkPad<AudioPacket> {

        private Exception mException = null;

        @Override
        public int available() {
            return mQueue.available() == 0 ? 1 : 0;
        }

        @Override
        public FilterErr offer( AudioPacket packet, long blockMicros ) {
            mException = null;

            // Check for empty packet.
            AudioFormat fmt = packet.audioFormat();
            if( fmt.sampleFormat() == Jav.AV_SAMPLE_FMT_NONE ) {
                mQueue.offer( packet );
                packet.ref();
                return FilterErr.DONE;
            }

            // Not empty. Run converter.
            AudioPacket p = null;
            try {
                p = mResampler.convert( packet );
            } catch( JavException e ) {
                return FilterErr.EXCEPTION;
            }

            if( p == null ) {
                return FilterErr.NONE;
            }

            mQueue.offer( p );
            p.deref();
            return FilterErr.DONE;
        }

        @Override
        public Exception exception() {
            return null;
        }

    }

}
