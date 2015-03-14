/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.pipe;

import bits.drawjav.*;
import bits.jav.Jav;
import bits.jav.util.JavMem;
import bits.microtime.*;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.*;
import java.util.*;


/**
 * @author Philip DeCamp
 */
public class LineOutFilter implements Filter, InPad<DrawPacket>, SyncClockControl {

    public static final int DEFAULT_BUFFER_LENGTH = 1024 * 256 * 2;

    private static final float MAX_VALUE = Short.MAX_VALUE;


    private final InPadReadyEvent REQUEST_EVENT = new InPadReadyEvent( this );


    private final int mRequestedBufferSize;

    private SourceDataLine mLine;
    private FloatControl   mGainControl;
    private int            mLineBufSize;


    private final Clock mClock;
    private final ClockState mClockState = new ClockState();

    private int  mChannels;
    private long mFrequency;
    private int  mBytesPerFrame;

    private final byte[]     mBuf          = new byte[8 * 1024];
    private       ByteBuffer mAltPacketBuf = null;

    private DrawPacket    mPacket                 = null;
    private FloatBuffer mPacketBuf              = null;
    private int         mPacketSamplesRemaining = 0;

    private EventBus  mBus       = null;
    private Exception mException = null;

    private volatile boolean mOpen         = false;
    private volatile boolean mStateChanged = true;


    public LineOutFilter( Clock optClock ) throws IOException {
        this( optClock, DEFAULT_BUFFER_LENGTH );
    }


    public LineOutFilter( Clock optClock, int bufferBytes ) throws IOException {
        mRequestedBufferSize = bufferBytes;
        mClock = optClock == null ? Clock.SYSTEM_CLOCK : optClock;
    }



    //// AudioLinePlayer methods /////


    public synchronized void setGainDbs( long execTimeMicros, double dbs ) {
        double gain = 20.0 * Math.log10( dbs );
        mGainControl.setValue( (float)gain );
    }


    public synchronized void setGainMult( long execTimeMicros, double mult ) {
        mGainControl.setValue( (float)mult );
    }


    synchronized void drainAudio() throws InterruptedException {
        while( mPacket != null ) {
            wait();
        }
        mLine.drain();
    }


    //// Filter Methods /////

    @Override
    public synchronized void open( EventBus bus ) {
        if( mOpen ) {
            return;
        }
        mOpen = true;
        mBus = bus;
        if( bus != null ) {
            bus.register( this );
        }
        new Thread( LineOutFilter.class.getName() ) {
            public void run() {
                playLoop();
            }
        }.start();
    }

    @Override
    public synchronized void close() {
        if( !mOpen ) {
            return;
        }
        mOpen         = false;
        mStateChanged = true;
        notifyAll();
    }

    @Override
    public boolean isOpen() {
        return mOpen;
    }

    @Override
    public int inputNum() {
        return 1;
    }

    @Override
    public InPad input( int idx ) {
        return this;
    }

    @Override
    public int outputNum() {
        return 0;
    }

    @Override
    public OutPad output( int idx ) {
        return null;
    }

    @Override
    public synchronized void clear() {
        if( mPacket != null ) {
            mPacket.deref();
            mPacket = null;
            mPacketBuf = null;
        }
        mLine.flush();
    }



    //// SinkPad Methods /////

    @Override
    public void config( Stream stream ) throws IOException {
        if( stream == null ) {
            return;
        }

        StreamFormat format = stream.format();
        if( format == null ) {
            throw new IllegalArgumentException( "Missing AudioFormat." );
        }

        mChannels = format.mChannels;
        mFrequency = format.mSampleRate;
        mBytesPerFrame = mChannels * 2;
        javax.sound.sampled.AudioFormat lineFormat = new javax.sound.sampled.AudioFormat( format.mSampleRate,
                                                                                          16,
                                                                                          mChannels,
                                                                                          true,
                                                                                          true );

        int bufBytes = mRequestedBufferSize * mChannels;
        bufBytes -= bufBytes % mBytesPerFrame;
        bufBytes = Math.max( bufBytes, mBytesPerFrame * 128 );
        mLineBufSize = bufBytes;
        DataLine.Info info = new DataLine.Info( SourceDataLine.class, lineFormat, bufBytes );

        try {
            mLine = (SourceDataLine)AudioSystem.getLine( info );
            mLine.open( lineFormat, bufBytes );
            mGainControl = (FloatControl)mLine.getControl( FloatControl.Type.MASTER_GAIN );
            mGainControl.setValue( 0f );
        } catch( LineUnavailableException ex ) {
            throw new IOException( ex.getMessage() );
        }
    }

    @Override
    public boolean isThreaded() {
        return true;
    }

    @Override
    public Object lock() {
        return this;
    }

    @Override
    public synchronized int status() {
        return !mOpen ? CLOSED :
                mPacket == null ? OKAY : WAIT;
    }

    @Override
    public synchronized int offer( DrawPacket packet ) {
        if( !mOpen ) {
            return CLOSED;
        }

        if( packet == null || packet.isGap() ) {
            return OKAY;
        }

        // Check format compatability.
        int chans = packet.channels();
        int sampFormat = packet.format();

        switch( sampFormat ) {
        case Jav.AV_SAMPLE_FMT_FLT:
            if( chans != mChannels ) {
                mException = new IllegalArgumentException( "Wrong channel number." );
                return EXCEPTION;
            }
            break;

        case Jav.AV_SAMPLE_FMT_FLTP:
            if( mChannels != 1 ) {
                mException = new IllegalArgumentException( "Wrong channel number." );
                return EXCEPTION;
            }
            break;

        default:
            mException = new IllegalArgumentException( "Unsupported sample format: " + sampFormat );
            return EXCEPTION;
        }

        // Wait for current packet to complete buffering.
        if( mPacket != null ) {
            return WAIT;
        }

        mPacket = packet;
        mPacket.ref();
        int frames = mPacket.nbSamples();
        mPacketSamplesRemaining = frames * mChannels;

        ByteBuffer bb = packet.javaBufElem( 0 );
        if( bb != null ) {
            mPacketBuf = bb.asFloatBuffer();
        } else {
            // Copy data to different buffer.
            int bytes = Math.min( packet.lineSize( 0 ), frames * mBytesPerFrame );
            if( mAltPacketBuf == null || bytes > mAltPacketBuf.capacity() ) {
                mAltPacketBuf = ByteBuffer.allocateDirect( bytes );
                mAltPacketBuf.order( ByteOrder.nativeOrder() );
            } else {
                mAltPacketBuf.clear();
            }

            mAltPacketBuf.limit( bytes );
            JavMem.copy( packet.data(), mAltPacketBuf );
            mAltPacketBuf.flip();
            mPacketBuf = mAltPacketBuf.asFloatBuffer();
        }

        notifyAll();
        return OKAY;
    }

    @Override
    public synchronized Exception exception() {
        Exception ret = mException;
        mException = null;
        return ret;
    }



    ///// Clock Control Methods //////

    @Subscribe
    public void processClockEvent( ClockEvent event ) {
        event.apply( this );
    }

    @Override
    public synchronized void clockStart( long exec ) {
        mClockState.clockStart( exec );
        mStateChanged = true;
        notifyAll();
    }

    @Override
    public synchronized void clockStop( long exec ) {
        mClockState.clockStop( exec );
        mStateChanged = true;
        notifyAll();
    }

    @Override
    public synchronized void clockSeek( long exec, long seek ) {
        mClockState.clockSeek( exec, seek );
        mStateChanged = true;
        notifyAll();
    }

    @Override
    public synchronized void clockRate( long exec, Frac rate ) {
        mStateChanged = true;
        notifyAll();
    }


    private synchronized void playLoop() {
        boolean isPlaying = false;

STATE_CHANGE:
        while( mOpen ) {
            mStateChanged = false;
            if( mClockState.mPlaying ) {
                // Start the audio line, if necessary.
                if( !isPlaying ) {
                    long waitUntil = mClockState.mMasterBasis / 1000L;
                    long waitMillis = waitUntil - mClock.micros() / 1000L;
                    if( waitMillis > 10L ) {
                        //Not sure about this loop, but I'm not about to mess with it.
                        do {
                            try {
                                wait( waitMillis );
                            } catch( InterruptedException ignored ) {
                            }
                            if( mStateChanged ) {
                                continue STATE_CHANGE;
                            }
                            writeToLine();
                            waitMillis = waitUntil - mClock.micros() / 1000L;
                        } while( waitMillis > 10L );
                    } else {
                        writeToLine();
                    }
                }

                mLine.start();
                isPlaying = true;

                while( !mStateChanged ) {
                    if( mPacket == null ) {
                        try {
                            wait();
                        } catch( InterruptedException ignored ) {
                        }
                    } else {
                        long waitTime = (mLineBufSize / 4 - mLine.available()) * 1000 / mFrequency;
                        if( waitTime > 10L ) {
                            try {
                                wait( waitTime );
                            } catch( InterruptedException ignored ) {
                            }
                        } else {
                            writeToLine();
                        }
                    }
                }
            } else {
                if( isPlaying ) {
                    long waitUntil = mClockState.mMasterBasis / 1000L;
                    long waitMillis = waitUntil - mClock.micros() / 1000L;

                    while( waitMillis > 10L ) {
                        try {
                            wait( waitMillis );
                        } catch( InterruptedException ignored ) {
                        }

                        if( mStateChanged ) {
                            continue STATE_CHANGE;
                        }
                        waitMillis = waitUntil - mClock.micros() / 1000L;
                    }

                    mLine.stop();
                    isPlaying = false;
                }

                while( !mStateChanged ) {
                    try {
                        wait();
                    } catch( InterruptedException ignored ) {}
                }
            }
        }
    }


    private int writeToLine() {
        final FloatBuffer fb = mPacketBuf;
        final byte[] arr = mBuf;
        int ret = 0;

        while( mPacket != null ) {
            //System.out.print( "LineOutFilter: " ); Debug.print( packet );

            int sampNum = Math.min( arr.length / 2, Math.min( mLine.available() / 2, mPacketSamplesRemaining ) );
            sampNum -= sampNum % mChannels;
            if( sampNum == 0 ) {
                return ret;
            }

            int revert = 0;
            if( fb != null ) {
                revert = fb.position();
                for( int i = 0; i < sampNum; i++ ) {
                    int s = (int)(fb.get() * MAX_VALUE);
                    mBuf[2*i  ] = (byte)((s >> 8) & 0xFF);
                    mBuf[2*i+1] = (byte)(s & 0xFF);
                }
            } else {
                Arrays.fill( mBuf, 0, 2 * sampNum, (byte)0 );
            }

            final int arrEnd = 2 * sampNum;
            int arrOff = 0;

            while( arrOff < arrEnd ) {
                int n = mLine.write( arr, arrOff, arrEnd - arrOff );
                arrOff += n;
                if( n <= 0 ) {
                    if( fb != null ) {
                        fb.position( revert );
                    }
                    return 0;
                }
            }

            mPacketSamplesRemaining -= arrOff / 2;
            if( mPacketSamplesRemaining <= 0 ) {
                mPacket.deref();
                mPacket = null;
                mPacketBuf = null;
                notifyAll();

                // Notify bus here.
                if( mBus != null ) {
                    mBus.post( REQUEST_EVENT );
                }
            }
        }

        return ret;
    }

}
