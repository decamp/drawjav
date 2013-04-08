package cogmac.javdraw;

import java.io.*;
import java.util.logging.*;

import javax.media.opengl.GL;

import cogmac.clocks.*;
import cogmac.draw3d.nodes.DrawNodeAdapter;


/**
 * Handles realtime processing of one source.
 * 
 * @author decamp
 */
public class SyncedDriver extends DrawNodeAdapter implements StreamDriver {
    
        
    private static Logger sLog = Logger.getLogger( SyncedDriver.class.getName() );
    
    private final PlayController mPlayCont;
    private final PassiveDriver mDriver;
    private final PlayHandler mPlayHandler;

    private boolean mNeedUpdate = true;
    private boolean mClosed     = false;
    private boolean mHasStream  = false;
    private boolean mNeedSeek = false;
    private boolean mEof      = false;
    private long mSeekMicros  = 0L;
    
    
    public SyncedDriver( PlayController playCont, Source source ) {
        mPlayCont    = playCont;
        mDriver      = new PassiveDriver( source );
        mPlayHandler = new PlayHandler();
        playCont.caster().addListener( mPlayHandler );
    }
    
    
    public long seekWarmupMicros() {
        return mDriver.seekWarmupMicros();
    }
    
    
    public void seekWarmupMicros( long micros ) {
        mDriver.seekWarmupMicros( micros );
    }
    
    
    public void videoPoolCap( int poolCap ) {
        mDriver.videoPoolCap( poolCap );
    }
    
    
    public void audioPoolCap( int cap ) {
        mDriver.audioPoolCap( cap );
    }
    
    
    public Source source() {
        return mDriver.source();
    }
    
    
    public PlayController playController() {
        return mPlayCont;
    }
    
    
    public StreamHandle openVideoStream( StreamHandle source,
                                         PictureFormat destFormat,
                                         Sink<? super VideoPacket> sink )
                                         throws IOException 
    {
        StreamHandle ret = mDriver.openVideoStream( source, destFormat, sink );
        if( ret == null ) {
            return null;
        }
        mHasStream = mDriver.hasSink();
        mNeedUpdate = true;
        return ret;
    }
    
    
    public synchronized StreamHandle openAudioStream( StreamHandle source,
                                                      AudioFormat format,
                                                      Sink<? super AudioPacket> sink )
                                                      throws IOException 
    {
        StreamHandle ret = mDriver.openAudioStream( source, format, sink );
        if( ret == null ) {
            return null;
        }
        mHasStream  = mDriver.hasSink();
        mNeedUpdate = true;
        return ret;
    }
    
    
    public synchronized boolean closeStream( StreamHandle stream ) throws IOException {
        boolean ret = mDriver.closeStream( stream );
        mHasStream  = mDriver.hasSink();
        mNeedUpdate = true;
        return ret;
    }
    
    
    public synchronized void close() throws IOException {
        if( mClosed ) {
            return;
        }
        mClosed     = true;
        mNeedUpdate = true;
        mDriver.close();
        mPlayCont.caster().removeListener( mPlayHandler );
    }
    
    
    public boolean isOpen() {
        return !mClosed;
    }
    
    
    
    public void pushDraw( GL gl ) {
        boolean seek    = false;
        long seekMicros = 0;
        long timeMicros = 0;
        
        synchronized( this ) {
            
            if( mNeedUpdate ) {
                if( mClosed ) {
                    return;
                }
                
                if( mNeedSeek ) {
                    mNeedSeek = false;
                    mEof      = false;
                    mDriver.seek( mSeekMicros );
                    mDriver.clear();
                }
                
                if( !mHasStream || mEof ) {
                    return;
                }
                
                timeMicros = mPlayCont.clock().micros();
            }
        }

        
        while( true ) {
            try {
                if( !mDriver.queue() ) {
                    continue;
                }
            } catch( EOFException ex ) {
                mEof = true;
                return;
            } catch( IOException ex ) {
                mEof = true;
                warn( "Failed to process packet.", ex );
            }
            
            if( mDriver.nextMicros() > timeMicros ) {
                return;
            }
            
            try {
                mDriver.send();
            } catch( IOException ex ) {
                sLog.log( Level.WARNING, "Failed to process packet", ex );
            }
        }
    }
    
    

    private synchronized void warn( String message, Exception ex ) {
        sLog.log( Level.WARNING, message, ex );
    }
        
    
    private final class PlayHandler implements PlayControl {

        public void playStart( long execMicros ) {}

        public void playStop( long execMicros ) {}

        public void seek( long execMicros, long seekMicros ) {
            synchronized( SyncedDriver.this ) {
                mNeedSeek   = true;
                mSeekMicros = seekMicros;
            }
        }

        public void setRate( long execMicros, double rate ) {}
        
    }
    
}
