package bits.drawjav.audio;

import bits.drawjav.*;
import bits.jav.Jav;
import bits.microtime.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import javax.sound.sampled.*;


/**
 * @author Philip DeCamp
 */
public class AudioLinePlayer implements Sink<AudioPacket>, PlayControl {

    public static final  int   DEFAULT_BUFFER_LENGTH = 1024 * 256 * 2;
    private static final int   LINE_BUFFER_SIZE      = 1024 * 256 * 2;
    private static final float MAX_VALUE             = Short.MAX_VALUE;

    private final javax.sound.sampled.AudioFormat mFormat;
    private final SourceDataLine                  mLine;
    private final FloatControl                    mGainControl;

    private final int    mChannels;
    private final long   mFrequency;
    private final int    mBytesPerFrame;

    private final byte[] mBuf;
    private int mBufIndex;
    private int mBufSize;

    private final FullClock mState;
    private boolean mClosed       = false;
    private boolean mStateChanged = false;


    public AudioLinePlayer( AudioFormat format ) throws IOException {
        this( format, Clock.SYSTEM_CLOCK, DEFAULT_BUFFER_LENGTH );
    }


    public AudioLinePlayer( AudioFormat format, Clock optClock ) throws IOException {
        this( format, optClock, DEFAULT_BUFFER_LENGTH );
    }


    public AudioLinePlayer( AudioFormat format, Clock optClock, int bufferBytes ) throws IOException {
        mChannels      = format.channels();
        mFormat        = new javax.sound.sampled.AudioFormat( format.sampleRate(), 16, mChannels, true, true );
        mFrequency     = format.sampleRate();
        mBytesPerFrame = mChannels * 2;

        bufferBytes -= bufferBytes % mBytesPerFrame;
        if( bufferBytes < mBytesPerFrame ) {
            throw new IllegalArgumentException( "0 buffer size" );
        }

        mBuf       = new byte[bufferBytes];
        mBufIndex  = 0;
        mBufSize   = 0;

        if( optClock == null ) {
            optClock = Clock.SYSTEM_CLOCK;
        }

        mState = new FullClock( optClock );
        DataLine.Info info = new DataLine.Info( SourceDataLine.class, mFormat, LINE_BUFFER_SIZE );

        try {
            mLine = (SourceDataLine)AudioSystem.getLine( info );
            mLine.open( mFormat, LINE_BUFFER_SIZE );
            mGainControl = (FloatControl)mLine.getControl( FloatControl.Type.MASTER_GAIN );
            mGainControl.setValue( 0f );
        } catch( LineUnavailableException ex ) {
            throw new IOException( ex.getMessage() );
        }

        new Thread( AudioLinePlayer.class.getName() ) {
            public void run() {
                playLoop();
            }
        }.start();

    }




    public synchronized void playStart( long execTimeMicros ) {
        //System.out.println( "Play on: " + execTimeMicros + "\t" + mState.masterMicros() );
        mState.playStart( execTimeMicros );
        mStateChanged = true;
        notifyAll();
    }


    public synchronized void playStop( long execTimeMicros ) {
        mState.playStop( execTimeMicros );
        mStateChanged = true;
        notifyAll();
    }


    public synchronized void seek( long execTimeMicros, long gotoTimeMicros ) {
        mState.seek( execTimeMicros, gotoTimeMicros );
        mStateChanged = true;
        notifyAll();
    }


    public synchronized void setRate( long execTimeMicros, double rate ) {
        mState.setRate( execTimeMicros, rate );
        mStateChanged = true;
        notifyAll();
    }



    public void consume( AudioPacket packet ) throws IOException {
        AudioFormat format = packet.audioFormat();
        if( format == null ) {
            return;
        }

        int chans = format.channels();
        if( chans != mChannels ) {
            return;
        }

        switch( format.sampleFormat() ) {
        case Jav.AV_SAMPLE_FMT_FLT:
            break;
        case Jav.AV_SAMPLE_FMT_FLTP:
            if( chans ==  1 ) {
                break;
            } else {
                return;
            }
        default:
            return;
        }

        ByteBuffer bb = packet.javaBufElem( 0 );
        if( bb == null ) {
            return;
        }

        FloatBuffer fb = bb.asFloatBuffer();
        consumeAudio( fb, fb.position(), packet.nbSamples() );
    }


    public void clear() {
        clearAudio();
    }


    public void close() {
        close( mState.masterMicros() );
    }


    public synchronized void close( long execTimeMicros ) {
        mClosed       = true;
        mStateChanged = true;
        notifyAll();
    }


    public boolean isOpen() {
        return !mClosed;
    }



    public synchronized void setGainDbs( long execTimeMicros, double dbs ) {
        double gain = 20.0 * Math.log10( dbs );
        mGainControl.setValue( (float)gain );
    }


    public synchronized void setGainMult( long execTimeMicros, double mult ) {
        mGainControl.setValue( (float)mult );
    }


    public synchronized int consumeAudio( float[] buf, int offset, int frames ) throws InterruptedIOException {
        if( frames <= 0 ) {
            return 0;
        }

        int writeFrames;

        while( true ) {
            writeFrames = ( mBuf.length - mBufSize ) / mBytesPerFrame;
            if( writeFrames > 0 ) {
                break;
            }
            try {
                wait();
            } catch( InterruptedException ex ) {
                throw new InterruptedIOException();
            }
        }

        writeFrames = Math.min( writeFrames, frames / mChannels );
        final int samplesRead = writeFrames * mChannels;

        while( writeFrames > 0 ) {
            int pos   = (mBufIndex + mBufSize) % mBuf.length;
            int samps = mChannels * Math.min( writeFrames, ( mBuf.length - pos ) / mBytesPerFrame );

            for( int i = 0; i < samps; i++ ) {
                int s = (int)( buf[offset + i] * MAX_VALUE );
                mBuf[pos + 2*i    ] = (byte)( (s >> 8) & 0xFF );
                mBuf[pos + 2*i + 1] = (byte)(  s       & 0xFF );
            }

            mBufSize    += samps * 2;
            offset      += samps;
            writeFrames -= samps / mChannels;
        }

        notifyAll();
        return samplesRead;
    }


    public synchronized int consumeAudio( FloatBuffer buf, int offset, int frames ) throws InterruptedIOException {
        if( frames <= 0 ) {
            return 0;
        }

        int writeFrames;
        while( true ) {
            writeFrames = ( mBuf.length - mBufSize ) / mBytesPerFrame;
            if( writeFrames > 0 ) {
                break;
            }
            try {
                wait();
            } catch( InterruptedException ex ) {
                throw new InterruptedIOException();
            }
        }

        writeFrames = Math.min( writeFrames, frames );
        final int samplesRead = writeFrames * mChannels;

        while( writeFrames > 0 ) {
            int pos   = ( mBufIndex + mBufSize ) % mBuf.length;
            int samps = mChannels * Math.min( writeFrames, ( mBuf.length - pos ) / mBytesPerFrame );

            for( int i = 0; i < samps; i++ ) {
                int s = (int)( buf.get( i + offset ) * MAX_VALUE );
                mBuf[pos + 2*i  ] = (byte)( (s >> 8) & 0xFF );
                mBuf[pos + 2*i+1] = (byte)(  s       & 0xFF );
            }

            mBufSize    += samps * 2;
            offset      += samps;
            writeFrames -= samps / mChannels;
        }

        notifyAll();
        return samplesRead;
    }


    public synchronized void clearAudio() {
        mBufSize  = 0;
        mBufIndex = 0;
        mLine.flush();
    }


    public synchronized void drainAudio() throws InterruptedException {
        while( mBufSize > 0 ) {
            wait();
        }
        mLine.drain();
    }



    private synchronized void playLoop() {
        boolean isPlaying = false;

STATE_CHANGE:
        while( !mClosed ) {
            mStateChanged = false;
            if( mState.isPlaying() ) {
                // Start the audio line, if necessary.
                if( !isPlaying ) {
                    long waitUntil  = mState.masterSyncMicros() / 1000L;
                    long waitMillis = waitUntil - mState.masterMicros() / 1000L;
                    if( waitMillis > 10L ) {
                        //Not sure about this loop, but I'm not about to mess with it.
                        do {
                            try {
                                wait( waitMillis );
                            } catch( InterruptedException ignored ) {}
                            if( mStateChanged ) {
                                continue STATE_CHANGE;
                            }
                            writeToLine();
                            waitMillis = waitUntil - mState.masterMicros() / 1000L;
                        } while( waitMillis > 10L );
                    } else {
                        writeToLine();
                    }
                }

                mLine.start();
                isPlaying = true;

                while( !mStateChanged ) {
                    if( mBufSize == 0 ) {
                        try {
                            wait();
                        } catch( InterruptedException ignored ) {}
                    } else {
                        long waitTime = (LINE_BUFFER_SIZE / 4 - mLine.available()) * 1000 / mFrequency;
                        if( waitTime > 10L ) {
                            try {
                                wait( waitTime );
                            } catch( InterruptedException ignored ) {}
                        } else {
                            writeToLine();
                        }
                    }
                }
            } else {
                if( isPlaying ) {
                    long waitUntil  = mState.masterSyncMicros() / 1000L;
                    long waitMillis = waitUntil - mState.masterMicros() / 1000L;

                    while( waitMillis > 10L ) {
                        try {
                            wait( waitMillis );
                        } catch( InterruptedException ignored ) {}

                        if( mStateChanged ) {
                            continue STATE_CHANGE;
                        }
                        waitMillis = waitUntil - mState.masterMicros() / 1000L;
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
        int startBytes = ( Math.min( mLine.available(), mBufSize ) / mBytesPerFrame ) * mBytesPerFrame;
        int bytesLeft  = startBytes;

        while( bytesLeft > 0 ) {
            int bytes = Math.min( mBytesPerFrame, mBuf.length - mBufIndex );
            bytes = mLine.write( mBuf, mBufIndex, bytes );
            mBufIndex = ( mBufIndex + bytes ) % mBuf.length;
            mBufSize  -= bytes;
            bytesLeft -= bytes;
            if( bytes == 0 ) {
                return 0;
            }
        }

        notifyAll();
        return startBytes;
    }



    @Deprecated public synchronized void setVolume( long execTimeMicros, double volume ) {
        setGainDbs( execTimeMicros, volume );
    }

}
