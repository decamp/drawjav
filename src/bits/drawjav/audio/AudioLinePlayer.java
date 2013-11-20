package bits.drawjav.audio;

import bits.clocks.*;
import bits.drawjav.*;
import bits.drawjav.AudioFormat;
import bits.jav.Jav;

import java.io.*;
import java.nio.FloatBuffer;

import javax.sound.sampled.*;



/** 
 * @author Philip DeCamp  
 */
public class AudioLinePlayer implements Sink<AudioPacket>, PlayControl {

    public static final int DEFAULT_BUFFER_LENGTH = 1024*256*2;
    private static final int LINE_BUFFER_SIZE      = 1024*256*2;
    private static final int MAX_VALUE             = Short.MAX_VALUE;
    
    private final javax.sound.sampled.AudioFormat mFormat;
    private final SourceDataLine mLine;
    private final FloatControl   mGainControl;
    private final long           mFrequency;
    private final byte[]         mBuf;
    
    private int mBufIndex;
    private int mBufSize;

    private final PlayClockChanger mState;
    private boolean mClosed = false;
    private boolean mStateChanged = false;
    
    
    public AudioLinePlayer( AudioFormat format ) throws IOException {
        this( format, Clock.SYSTEM_CLOCK, DEFAULT_BUFFER_LENGTH );
    }
    
    
    public AudioLinePlayer( AudioFormat format, Clock clock ) throws IOException {
        this( format, clock, DEFAULT_BUFFER_LENGTH );
    }
    
    
    public AudioLinePlayer( AudioFormat format, Clock clock, int bufferLength ) throws IOException {
        bufferLength &= 0xFFFFFFFE;
        if( bufferLength < 2 ) {
            throw new IllegalArgumentException( "0 buffer size" );
        }
        
        mBuf      = new byte[bufferLength];
        mBufIndex = 0;
        mBufSize  = 0; 
        
        mFormat    = new javax.sound.sampled.AudioFormat(format.sampleRate(), 16, format.channels(), true, true);
        mFrequency = format.sampleRate();
        
        if( clock == null ) {
            clock = Clock.SYSTEM_CLOCK;
        }
            
        mState = new PlayClockChanger( clock );
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
        System.out.println( "Play on: " + execTimeMicros + "\t" + mState.masterMicros() );
        mState.playStart( execTimeMicros );
        mStateChanged = true;
        notifyAll();
    }
    
    
    public synchronized void playStop( long execTimeMicros ) {
        mState.playStop( execTimeMicros );
        mStateChanged = true;
        notifyAll();
    }
    
    
    public synchronized void close( long execTimeMicros ) {
        mClosed = true;
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

    
    public synchronized void setVolume( long execTimeMicros, double volume ) {
        double gain = 20.0 * Math.log10( volume );
        mGainControl.setValue( (float) gain );
    }
    
    
    public synchronized int consumeAudio( float[] buf, int offset, int length ) throws InterruptedIOException {
        if( length <= 0 ) {
            return 0;
        }
        
        int toWrite = ( mBuf.length - mBufSize ) / 2;
        while( toWrite < 1 ) {
            try {
                wait();
            } catch( InterruptedException ex ) {
                throw new InterruptedIOException();
            }

            toWrite = ( mBuf.length - mBufSize) / 2;
        }

        toWrite = length = Math.min( toWrite, length );
        
        while( toWrite > 0 ) {
            int pos = ( mBufIndex + mBufSize) % mBuf.length;
            int n = Math.min( toWrite, ( mBuf.length - pos) / 2 );

            for( int i = 0; i < n; i++ ) {
                int s = (int)( buf[offset+i] * MAX_VALUE );
                mBuf[pos+i*2  ] = (byte)( ( s >> 8 ) & 0xFF );
                mBuf[pos+i*2+1] = (byte)( s & 0xFF );
            }
            
            mBufSize += n*2;
            offset += n;
            toWrite -= n;
        }
        
        notifyAll();
        return length;
    }
    

    public synchronized int consumeAudio( FloatBuffer buf, int offset, int length ) throws InterruptedIOException {
        if( length <= 0 ) {
            return 0;
        }
        
        int toWrite = ( mBuf.length - mBufSize ) / 2;
        while( toWrite < 1 ) {
            try {
                wait();
            } catch( InterruptedException ex ) {
                throw new InterruptedIOException();
            }

            toWrite = ( mBuf.length - mBufSize) / 2;
        }

        toWrite = length = Math.min( toWrite, length );
        
        while( toWrite > 0 ) {
            int pos = ( mBufIndex + mBufSize) % mBuf.length;
            int n = Math.min( toWrite, ( mBuf.length - pos) / 2 );

            for( int i = 0; i < n; i++ ) {
                int s = (int)( buf.get( i + offset ) * MAX_VALUE );
                mBuf[pos+i*2  ] = (byte)( ( s >> 8 ) & 0xFF );
                mBuf[pos+i*2+1] = (byte)( s & 0xFF );
            }
            
            mBufSize += n*2;
            offset += n;
            toWrite -= n;
        }
        
        notifyAll();
        return length;
    }
    
    
    
    public synchronized void clearAudio() {
        mBufSize = 0;
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
                            } catch( InterruptedException ex ) {}
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
                        } catch( InterruptedException ex ) {}
                    } else {
                        long waitTime = (LINE_BUFFER_SIZE / 4 - mLine.available()) * 1000 / mFrequency;
                        if( waitTime > 10L ) {
                            try{
                                wait( waitTime );
                            } catch( InterruptedException ex ) {}
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
                        } catch( InterruptedException ex ){}
                        
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
                    } catch( InterruptedException ex ) {}
                }
            }
        }
    }
        
    
    
    private int writeToLine() {
        int startToWrite = Math.min(mLine.available(), mBufSize);
        int toWrite      = startToWrite;
        
        while(toWrite > 1) {
            int n = Math.min(toWrite, mBuf.length - mBufIndex);
            n = mLine.write(mBuf, mBufIndex, n);
            mBufIndex = (mBufIndex + n) % mBuf.length;
            mBufSize -= n;
            toWrite -= n;
            
            if(n == 0)
                return 0;
        }
        
        notifyAll();
        return startToWrite;
    }
        

    

    public void clear() {
        clearAudio();
    }

    
    public void close() {
        close( mState.masterMicros() );
    }
    

    public boolean isOpen() {
        return !mClosed;
    }
    
    
    public void consume( AudioPacket packet ) throws IOException  {
        if( packet == null || !packet.hasDirectBuffer() ) {
            return;
        }
        
        AudioFormat format = packet.audioFormat();
        if( format == null || format.sampleFormat() != Jav.AV_SAMPLE_FMT_FLT ) {
            return;
        }
        
        FloatBuffer bb = packet.directBuffer().asFloatBuffer();
        consumeAudio( bb, bb.position(), packet.nbSamples() );
    }


}
