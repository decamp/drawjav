package cogmac.drawjav;

import java.io.*;
import java.nio.channels.ClosedChannelException;
import java.util.logging.*;

import cogmac.clocks.*;


/**
 * Handles processing of one source. PassiveDriver is NOT THREAD SAFE 
 * and is meant to be used by a single controlling thread.
 * PassiveDriver is intended to be a component that may be used to
 * build more complex and complete drivers.
 * 
 * @author decamp
 */
public class PassiveDriver implements StreamDriver {
    
    private static Logger sLog = Logger.getLogger( SyncedDriver.class.getName() );
    
    private final Source mSource;
    private final ReformatPipe mSink = new ReformatPipe();
    
    private boolean mClosed        = false;
    private boolean mHasStream     = false;
    private boolean mNeedSeek      = false;
    private long mSeekMicros       = Long.MIN_VALUE;
    private long mSeekWarmupMicros = 500000L;
    
    private Packet mNextPacket = null;
    
    
    public PassiveDriver( Source source ) {
        mSource = source;
    }
    
    
    
    public long seekWarmupMicros() {
        return mSeekWarmupMicros;
    }
    
    
    public void seekWarmupMicros( long micros ) {
        mSeekWarmupMicros = micros;
    }
    
    
    public int videoPoolCap() {
        return mSink.videoPoolCap();
    }
    
    
    public void videoPoolCap( int cap ) {
        mSink.videoPoolCap( cap );
    }

    
    public int audioPoolCap() {
        return mSink.audioPoolCap();
    }
    
    
    public void audioPoolCap( int cap ) {
        mSink.audioPoolCap( cap );
    }
    
    
    public Source source() {
        return mSource;
    }
    
    
    public PlayController playController() {
        return null;
    }
    
    
    public synchronized StreamHandle openVideoStream( StreamHandle source,
                                                      PictureFormat destFormat,
                                                      Sink<? super VideoPacket> sink )
                                                      throws IOException 
    {
        boolean active   = mSink.isSourceActive( source );
        StreamHandle ret = mSink.openVideoStream( source, destFormat, sink );
        if( ret == null ) {
            return null;
        }
        
        if( !active ) {
            try {
                mSource.openStream( source );
            } catch( IOException ex ) {
                mSink.closeStream( ret );
                throw ex;
            }
        }
        
        return ret;
    }
    
    
    public synchronized StreamHandle openAudioStream( StreamHandle source,
                                                      AudioFormat format,
                                                      Sink<? super AudioPacket> sink )
                                                      throws IOException 
    {
        boolean active   = mSink.isSourceActive( source );
        StreamHandle ret = mSink.openAudioStream( source, format, sink );
        if( ret == null ) {
            return null;
        }
        
        if( !active ) {
            try {
                mSource.openStream( source );
            } catch( IOException ex ) {
                mSink.closeStream( ret );
                throw ex;
            }
        }
        
        return ret;
    }

    
    public boolean closeStream( StreamHandle stream ) throws IOException {
        if( !mSink.closeStream( stream ) ) {
            return false;
        }
        
        synchronized( this ) {
            StreamHandle orig = mSink.destToSource( stream );
            if( orig == null ) {
                return true;
            }
            
            if( !mSink.isSourceActive( orig ) ) {
                try {
                    mSource.closeStream( orig );
                } catch( IOException ex ) {
                    warn( "Failed to close source stream.", ex );
                }
            }
            
            return true;
        }
    }
    
    
    public void close() throws IOException {
        synchronized( this ) {
            if( mClosed ) {
                return;
            }
            mClosed = true;
        }
        mSink.close();
        mSource.close();
    }
    
    
    public boolean isOpen() {
        return !mClosed;
    }

    
    public boolean hasSink() {
        return mSink.hasSink();
    }
    
    
    public synchronized void seek( long micros ) {
        mNeedSeek   = true;
        mSeekMicros = micros;
    }
        
    
    public synchronized boolean queue() throws IOException {
        if( mClosed ) {
            throw new ClosedChannelException();
        }
        
        if( mNeedSeek ) {
            mNeedSeek = false;
            if( mNextPacket != null ) {
                mNextPacket.deref();
                mNextPacket = null;
            }
            mSource.seek( mSeekMicros - mSeekWarmupMicros );
        } else if( mNextPacket != null ) {
            return true;
        }
        
        Packet p = mSource.readNext();
        if( p == null ) {
            return false;
        }
        if( p.getStartMicros() < mSeekMicros ) {
            p.deref();
            return false;
        }
        
        mNextPacket = p;
        return true;
    }
    
    
    public long nextMicros() {
        return mNextPacket == null ? Long.MIN_VALUE : mNextPacket.getStartMicros();
    }
    
    
    public void clear() {
        synchronized( this ) {
            if( mNextPacket != null ) {
                mNextPacket.deref();
                mNextPacket = null;
            }
        }

        mSink.clear();
    }
    
    
    public boolean send() throws IOException {
        Packet p;
                
        synchronized( this ) {
            if( mNextPacket == null ) {
                return false;
            }
            
            p = mNextPacket;
            mNextPacket = null;
        }
        
        try {
            mSink.consume( p );
            p.deref();
            return true;
        } catch( InterruptedIOException ex ) {
            p.deref();
            p = null;
            return false;
        } catch( IOException ex ) {
            warn( "Failed to process packet", ex );
            return false;
        }
    }
    
    
    private void warn( String message, Exception ex ) {
        sLog.log( Level.WARNING, message, ex );
    }
    
    
}
