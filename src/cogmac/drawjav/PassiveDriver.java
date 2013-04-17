package cogmac.drawjav;

import java.io.*;
import java.nio.channels.ClosedChannelException;
import java.util.logging.*;

import cogmac.clocks.*;


/**
 * PassiveDriver is not a real driver in that it does not handle
 * threading, but is meant as a general purpose component that
 * can simplify IO while supporting a variety of threading and
 * scheduling strategies. PassiveDriver can handle reading from
 * a single Source object, and delivery to multiple sinks.
 * 
 * @author decamp
 */
public class PassiveDriver implements StreamDriver {
    
    private static Logger sLog = Logger.getLogger( SyncedDriver.class.getName() );
    
    private final Source mSource;
    private final ManyToManyFormatter mSink = new ManyToManyFormatter();
    
    private boolean mClosed         = false;
    private boolean mReadable       = true;
    private boolean mEof            = false;
    private IOException mErrorState = null;
    
    private boolean mNeedSeek      = false;
    private long mSeekMicros       = Long.MIN_VALUE;
    private long mSeekWarmupMicros = 500000L;
    private boolean mNeedClear     = false;
    private boolean mClearOnSeek   = true;
    
    private Packet mNextPacket = null;
    
    
    public PassiveDriver( Source source ) {
        mSource = source;
    }
    
    
    
    public boolean canRead() {
        return mReadable;
    }
    
    
    public boolean eof() {
        return mEof;
    }
    
    
    public boolean hasSink() {
        return mSink.hasSink();
    }
    
    
    public IOException error() {
        return mErrorState;
    }
    
    
    
    public synchronized void seek( long micros ) {
        mNeedSeek   = true;
        mSeekMicros = micros;
        mEof        = false;
        mErrorState = null;
        if( mNextPacket != null ) {
            mNextPacket.deref();
            mNextPacket = null;
        }
        updateStatus();
    }
        
    
    public synchronized boolean queueNext() {
        if( !mReadable ) {
            return false;
        }
        
        if( mNeedSeek ) {
            mNeedSeek  = false;
            mNeedClear = true;
            
            try {
                mSource.seek( mSeekMicros - mSeekWarmupMicros );
            } catch( IOException ex ) {
                mErrorState = ex;
                updateStatus();
                return false;
            }
        
        } else if( mNextPacket != null ) {
            return true;
        }
        
        try {
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
        } catch( InterruptedIOException ex ) {
        } catch( EOFException ex ) {
            mEof = true;
            updateStatus();
        } catch( IOException ex ) {
            mErrorState = ex;
            updateStatus();
        }
        
        return false;
    }
    
    
    public boolean hasNext() {
        return mNextPacket != null;
    }
    
    
    public synchronized Packet next() {
        if( mNextPacket == null ) {
            return null;
        }
        mNextPacket.ref();
        return mNextPacket;
    }
    
    
    public synchronized long nextMicros() {
        return mNextPacket == null ? Long.MIN_VALUE : mNextPacket.getStartMicros();
    }
    

    public boolean sendNext() {
        Packet packet;
        boolean clear;
        
        synchronized( this ) {
            if( mNextPacket == null ) {
                return false;
            }
            
            packet = mNextPacket;
            mNextPacket = null;
            clear = mNeedClear;
            mNeedClear = false;
        }
        
        try {
            if( clear ) {
                mSink.clear();
            }
            mSink.consume( packet );
            packet.deref();
            return true;
        } catch( InterruptedIOException ex ) {
            packet.deref();
            packet = null;
            return false;
        } catch( IOException ex ) {
            warn( "Failed to process packet", ex );
            synchronized( this ) {
                if( !mNeedSeek ) {
                    mErrorState = ex;
                    updateStatus();
                }
            }
            
            return false;
        }
    }
    
    
    public boolean send( Packet packet ) {
        boolean clear;
        
        synchronized( this ) {
            clear = mNeedClear;
            mNeedClear = false;
        }
        
        try {
            if( clear ) {
                mSink.clear();
            }
            mSink.consume( packet );
            return true;
        } catch( InterruptedIOException ex ) {
            return false;
        } catch( IOException ex ) {
            warn( "Failed to process packet", ex );
            synchronized( this ) {
                if( !mNeedSeek ) {
                    mErrorState = ex;
                    updateStatus();
                }
            }
            return false;
        }
    }
    
    
    public synchronized void clearNext() {
        if( mNextPacket != null ) {
            mNextPacket.deref();
            mNextPacket = null;
        }
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
    
    
    public boolean clearOnSeek() {
        return mClearOnSeek;
    }
    
    
    public void clearOnSeek( boolean clearOnSeek ) {
        mClearOnSeek = clearOnSeek;
    }
    
    
    
    public void start() {}
    
    
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
        if( mClosed ) {
            throw new ClosedChannelException();
        }
        
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
        
        updateStatus();
        return ret;
    }
    
    
    public synchronized StreamHandle openAudioStream( StreamHandle source,
                                                      AudioFormat format,
                                                      Sink<? super AudioPacket> sink )
                                                      throws IOException 
    {
        if( mClosed ) {
            throw new ClosedChannelException();
        }
        
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
        
        updateStatus();
        return ret;
    }

    
    public boolean closeStream( StreamHandle stream ) throws IOException {
        if( !mSink.closeStream( stream ) ) {
            return false;
        }
        
        // Check if need to close source.
        synchronized( this ) {
            StreamHandle sourceStream = mSink.destToSource( stream );
            if( sourceStream != null && !mSink.isSourceActive( sourceStream ) ) {
                if( !mSink.isSourceActive( sourceStream ) ) {
                    try {
                        mSource.closeStream( sourceStream );
                    } catch( IOException ex ) {
                        warn( "Failed to close source stream.", ex );
                    }
                }
            }
        
            updateStatus();
            return true;
        }
    }
    
    
    public void close() {
        close( true );
    }
    
    
    public void close( boolean closeSource ) {
        synchronized( this ) {
            if( mClosed ) {
                return;
            }
            
            mClosed = true;
            updateStatus();
            
            if( mNextPacket != null ) {
                mNextPacket.deref();
                mNextPacket = null;
            }
        }
        
        mSink.close();
        
        if( closeSource ) {
            try {
                mSource.close();
            } catch( IOException ex ) {
                warn( "Failed to close av source.", ex );
            }
        }
    }
    
    
    public boolean isOpen() {
        return !mClosed;
    }
    
    
    
    private synchronized void updateStatus() {
        mReadable = !mClosed && !mEof && mErrorState == null && mSink.hasSink();
    }
    
    
    private static void warn( String message, Exception ex ) {
        sLog.log( Level.WARNING, message, ex );
    }

    
    
}
