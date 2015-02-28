package bits.drawjav.pipe;

import bits.drawjav.MemoryManager;
import bits.drawjav.StreamHandle;
import bits.drawjav.audio.*;
import bits.jav.Jav;
import bits.jav.util.JavMem;
import bits.microtime.ClockEvent;
import bits.util.ref.Refable;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import java.nio.*;


/**
 * @author Philip DeCamp
 */
public class SolaFilter implements Filter {

    private final MemoryManager mMem;

    private boolean  mOpen = false;
    private Sola     mSola = null;
    private EventBus mBus  = null;

    private final Input  mInput  = new Input();
    private final Output mOutput = new Output();

    private StreamHandle   mStream     = null;
    private AudioFormat    mFormat     = null;
    private AudioAllocator mAlloc      = null;
    private AudioPacket    mSrc        = null;
    private FloatBuffer    mSrcBuf     = null;
    private FloatBuffer    mWorkFloats = null;
    private ByteBuffer     mWorkBytes  = null;

    private AudioPacket    mDst        = null;
    private FloatBuffer    mDstBuf     = null;
    private long           mDstStart   = Long.MIN_VALUE;
    private long           mDstStop    = Long.MIN_VALUE;
    private boolean        mNeedDrain  = false;


    public SolaFilter( MemoryManager mem ) {
        mMem = mem;
    }


    @Override
    public int inputNum() {
        return 1;
    }

    @Override
    public InPad input( int idx ) {
        return mInput;
    }

    @Override
    public int outputNum() {
        return 0;
    }

    @Override
    public OutPad output( int idx ) {
        return mOutput;
    }

    @Override
    public void open( EventBus bus ) {
        if( mOpen ) {
            return;
        }
        if( mFormat == null ) {
            throw new IllegalStateException( "Filter not configured." );
        }
        mBus = bus;
        bus.register( new ClockHandler() );
        mSola = new Sola( mFormat.sampleRate() );
        mAlloc = mMem.audioAllocator( mStream, mFormat );
        mOpen = true;
    }

    @Override
    public void close() {
        if( !mOpen ) {
            return;
        }
        clear();
        mOpen = false;
        if( mAlloc != null ) {
            mAlloc.deref();
            mAlloc = null;
        }
    }

    @Override
    public boolean isOpen() {
        return mOpen;
    }

    @Override
    public void clear() {
        if( !mOpen ) {
            return;
        }
        mSola.clear();
        if( mSrc != null ) {
            mSrc.deref();
            mSrc = null;
        }
        mSrcBuf = null;
        if( mDst != null ) {
            mDst.deref();
            mDst = null;
        }
        mDstBuf = null;
        mNeedDrain = false;
        mDstStart = Long.MIN_VALUE;
        mDstStop  = Long.MIN_VALUE;
    }


    private void doProcess() {
        if( mDst == null ) {
            int len = mSrc.nbSamples();
            mDst = mAlloc.alloc( mFormat, len );
            mDstBuf = mDst.javaBufElem( 0 ).asFloatBuffer();
            mDstBuf.position( 0 ).limit( len );
            if( mDstStart == Long.MIN_VALUE ) {
                mDstStart = mDstStop = mSrc.startMicros();
            }
            mNeedDrain = false;
        }

        while( mDstBuf.remaining() > 0 && mSrcBuf.remaining() > 0 ) {
            int n = mSola.process( mSrcBuf, mDstBuf );
            if( n <= 0 ) {
                break;
            }
        }

        // Check if output is finished.
        if( mDstBuf.remaining() < 2 ) {
            // Compute approximate time bounds of output packet.
            double p = (double)mSrcBuf.position() / mSrcBuf.limit();
            long t0 = mSrc.startMicros();
            long t1 = mSrc.stopMicros();
            long mDstStop = t0 + (long)( p * ( t1 - t0 ) );
            mDst.init( mStream, mDstStart, mDstStop, mFormat, false );
            mDstStart = mDstStop;
            mNeedDrain = true;
        }

        // Check if input is finished.
        if( mSrcBuf.remaining() == 0 ) {
            mSrc.deref();
            mSrc = null;
        }
    }


    private class ClockHandler {
        @Subscribe
        public void processEvent( ClockEvent ev ) {
            if( ev.mId == ClockEvent.CLOCK_RATE ) {
                mSola.rate( ev.mRate );
            }
        }
    }


    private final class Input extends InPadAdapter<AudioPacket> {

        @Override
        public int status() {
            return !mOpen ? CLOSED :
                   mSrc == null ? OKAY : DRAIN_FILTER;
        }

        @Override
        public int offer( AudioPacket packet ) {
            if( !mOpen ) {
                return CLOSED;
            }
            if( mSrc != null ) {
                return DRAIN_FILTER;
            }

            mSrc = packet;
            packet.ref();

            ByteBuffer bb = packet.javaBufElem( 0 );
            if( bb != null ) {
                mSrcBuf = bb.asFloatBuffer();
            } else {
                int len = packet.lineSize( 0 );
                if( mWorkBytes == null || mWorkBytes.capacity() < len ) {
                    mWorkBytes = ByteBuffer.allocateDirect( len );
                    mWorkBytes.order( ByteOrder.nativeOrder() );
                    mWorkFloats = mWorkBytes.asFloatBuffer();
                }
                mWorkBytes.position( 0 ).limit( len );
                JavMem.copy( packet.dataElem( 0 ), mWorkBytes );
                mWorkFloats.position( 0 ).limit( len / 4 );
                mSrcBuf = mWorkFloats;
            }

            doProcess();
            return OKAY;
        }

        @Override
        public void config( StreamHandle stream ) {
            AudioFormat format = stream.audioFormat();
            if( format == null ) {
                throw new IllegalArgumentException( "Missing audio format" );
            }
            if( format.sampleFormat() != Jav.AV_SAMPLE_FMT_FLT ) {
                throw new IllegalArgumentException( "Unsupported sample format." );
            }
            if( format.channels() != 1 ) {
                throw new IllegalArgumentException( "Only mono is supported." );
            }

            mFormat = format;
        }

    }


    private final class Output extends OutPadAdapter {

        @Override
        public int status() {
            return !mOpen ? CLOSED :
                    mSrc != null ? OKAY : FILL_FILTER;
        }

        @Override
        public int poll( Refable[] out ) {
            if( !mOpen ) {
                return CLOSED;
            }

            while( true ) {
                if( mNeedDrain ) {
                    mNeedDrain = false;
                    out[0] = mDst;
                    mDst = null;

                    // Process as much as possible.
                    if( mSrc != null ) {
                        doProcess();
                    }
                    return OKAY;
                }

                // Check if anything to process.
                if( mSrc == null ) {
                    return FILL_FILTER;
                }

                doProcess();
            }
        }

    }


}
