package bits.drawjav.pipe;

import bits.drawjav.*;
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
public class SolaUnit implements AvUnit {

    private final MemoryManager mMem;

    private boolean  mOpen = false;
    private Sola     mSola = null;
    private EventBus mBus  = null;

    private final InHandler  mInput  = new InHandler();
    private final OutHandler mOutput = new OutHandler();

    private Stream         mStream     = null;
    private StreamFormat   mFormat     = null;
    private DrawPacket     mSrc        = null;
    private FloatBuffer    mSrcBuf     = null;
    private FloatBuffer    mWorkFloats = null;
    private ByteBuffer     mWorkBytes  = null;

    private PacketAllocator<DrawPacket> mAlloc = null;

    private DrawPacket  mDst        = null;
    private FloatBuffer mDstBuf     = null;
    private long        mDstStart   = Long.MIN_VALUE;
    private long        mDstStop    = Long.MIN_VALUE;
    private boolean     mOutPadFull = false;


    public SolaUnit( MemoryManager mem ) {
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
        mSola = new Sola( mFormat.mSampleRate );
        mAlloc = mMem.allocator( mFormat );
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
        mOutPadFull = false;
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
            mOutPadFull = false;
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
            mDst.init( mFormat, mDstStart, mDstStop, false );
            mDst.stream( mStream );
            mDstStart = mDstStop;
            mOutPadFull = true;
        }

        // Check if input is finished.
        if( mSrcBuf.remaining() == 0 ) {
            mSrc.deref();
            mSrc = null;
            mSrcBuf = null;
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


    private final class InHandler extends InPadAdapter<DrawPacket> {
        @Override
        public int status() {
            return !mOpen ? CLOSED :
                   mSrc == null ? OKAY : DRAIN_UNIT;
        }

        @Override
        public int offer( DrawPacket packet ) {
            if( !mOpen ) {
                return CLOSED;
            }
            if( mSrc != null ) {
                return DRAIN_UNIT;
            }

            if( packet.isGap() ) {
                return OKAY;
            }

            mSrc = packet;
            mSrc.ref();

            ByteBuffer bb = packet.javaBufElem( 0 );
            if( bb != null ) {
                bb.position( 0 ).limit( packet.nbSamples() * 4 );
                mSrcBuf = bb.asFloatBuffer();
            } else {
                int cap = packet.nbSamples() * 4;
                if( mWorkBytes == null || mWorkBytes.capacity() < cap ) {
                    mWorkBytes = ByteBuffer.allocateDirect( cap );
                    mWorkBytes.order( ByteOrder.nativeOrder() );
                    mWorkFloats = mWorkBytes.asFloatBuffer();
                }
                mWorkBytes.position( 0 ).limit( cap );
                JavMem.copy( packet.dataElem( 0 ), mWorkBytes );
                mWorkFloats.position( 0 ).limit( cap / 4 );
                mSrcBuf = mWorkFloats;
            }

            doProcess();
            return OKAY;
        }

        @Override
        public void config( StreamFormat format ) {
            if( format == null ) {
                throw new IllegalArgumentException( "Missing audio format" );
            }
            if( format.mSampleFormat != Jav.AV_SAMPLE_FMT_FLT ) {
                throw new IllegalArgumentException( "Unsupported sample format." );
            }
            if( format.mChannels != 1 ) {
                throw new IllegalArgumentException( "Only mono is supported." );
            }

            mFormat = format;
        }
    }


    private final class OutHandler extends OutPadAdapter {
        @Override
        public int status() {
            return !mOpen ? CLOSED :
                    mSrc != null ? OKAY : FILL_UNIT;
        }

        @Override
        public int poll( Refable[] out ) {
            if( !mOpen ) {
                return CLOSED;
            }

            while( true ) {
                if( mOutPadFull ) {
                    mOutPadFull = false;
                    out[0]  = mDst;
                    mDst    = null;
                    mDstBuf = null;

                    // Process as much as possible.
                    if( mSrc != null ) {
                        doProcess();
                    }
                    return OKAY;
                }

                // Check if anything to process.
                if( mSrc == null ) {
                    return FILL_UNIT;
                }

                doProcess();
            }
        }
    }

}
