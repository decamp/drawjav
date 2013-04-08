package cogmac.javdraw;

import java.io.*;
import java.util.logging.*;
import cogmac.clocks.*;

/**
 * TODO: This whole stupid class.
 * Handles realtime processing of one source.
 * 
 * @author decamp
 */
public class RealtimeDriver implements StreamDriver {
    
    public static RealtimeDriver newInstance( PlayController playCont,
                                              Source source ) 
    {
        return newInstance( playCont, source, null );
    }
    
    
    public static RealtimeDriver newInstance( PlayController playCont,
                                              Source source,
                                              PacketSyncer syncer ) 
    {
        if( syncer == null ) {
            syncer = new PacketSyncer( playCont );
        }
        return new RealtimeDriver( playCont, source, syncer );
    }
    
    
    private static Logger sLog = Logger.getLogger( RealtimeDriver.class.getName() );
    
    private final PlayController mPlayCont;
    private final PassiveDriver mDriver;
    private final PacketSyncer mSyncer;
    private final PlayHandler mPlayHandler;
    private final Thread mThread;
    
    private boolean mNeedUpdate    = false;
    private boolean mClosed        = false;
    private boolean mHasStream     = false;
    private boolean mEof           = false;
    private boolean mNeedSeek      = true;
    private long mSeekMicros       = 0L;
    
    private boolean mCanInterrupt  = false;
    
    
    private RealtimeDriver( PlayController playCont,
                            Source source,
                            PacketSyncer syncer )
    {
        mPlayCont    = playCont;
        mDriver      = new PassiveDriver( source );
        mSyncer      = syncer;
        mPlayHandler = new PlayHandler();
        mPlayCont.caster().addListener( mPlayHandler );
        mDriver.seek( mPlayCont.clock().micros() );
        
        mThread = new Thread(RealtimeDriver.class.getSimpleName()) {
            public void run() {
                runLoop();
            }
        };
        
        mThread.setDaemon(true);
        mThread.setPriority(Thread.NORM_PRIORITY - 1);
        mThread.start();
    }
    
    
    
    public synchronized void start() {
        if( mThread != null && !mThread.isAlive() ) {
            mThread.start();
        }
    }
    
    
    public void seekWarmupMicros( long micros ) {
        mDriver.seekWarmupMicros( micros );
    }
    
    
    public void videoPoolCap( int cap ) {
        mDriver.videoPoolCap( cap );
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
        synchronized( this ) {
            Sink syncedSink = mSyncer.openStream( sink );
            StreamHandle s = mDriver.openVideoStream( source, destFormat, syncedSink );
            if( s == null ) {
                syncedSink.close();
                return null;
            }
            mHasStream = mDriver.hasSink();
            mNeedUpdate = true;
            notifyAll();
            return s;
        }
    }
    
    
    public StreamHandle openAudioStream( StreamHandle source,
                                         AudioFormat format,
                                         Sink<? super AudioPacket> sink )
                                         throws IOException 
    {
        synchronized( this ) {
            Sink syncedSink = mSyncer.openStream( sink );
            StreamHandle s = mDriver.openAudioStream( source, format, syncedSink );
            if( s == null ) {
                syncedSink.close();
                return null;
            }
            mHasStream = mDriver.hasSink();
            mNeedUpdate = true;
            notifyAll();
            return s;
        }
    }
    
    
    public boolean closeStream( StreamHandle stream ) throws IOException {
        synchronized( this ) {
            boolean ret = mDriver.closeStream( stream );
            if( !ret ) {
                return false;
            }
            
            mHasStream = mDriver.hasSink();
            mNeedUpdate = true;
            return true;
        }
    }
    
    
    public synchronized void close() {
        if( mClosed ) {
            return;
        }
        
        mClosed     = true;
        mNeedUpdate = true;
        notifyAll();
        
        try {
            mDriver.close();
        } catch( IOException ex ) {
            warn( "Failed to close stream.", ex );
        }
    
        if( mCanInterrupt ) {
            mThread.interrupt();
        }
    }
    
    
    public boolean isOpen() {
        return !mClosed;
    }
    
    
    
    private void runLoop() {
        boolean sendPacket = false;
        
        while( true ) {
            synchronized( this ) {
                mCanInterrupt = false;
                
                if( mNeedUpdate ) {
                    Thread.interrupted();
                    
                    if( mClosed ) {
                        break;
                    }
                    
                    if( mNeedSeek ) {
                        mNeedSeek  = false;
                        mEof       = false;
                        sendPacket = false;
                        mDriver.seek( mSeekMicros );
                        mDriver.clear();
                        continue;
                    }
                    
                    if( !mHasStream || mEof ) {
                        try {
                            wait();
                        } catch( InterruptedException ex ) {}
                        continue;
                    }
                    
                    mNeedUpdate = false;
                }
                
                mCanInterrupt = sendPacket;
            }
            
            if( sendPacket ) {
                sendPacket = false;
                
                try {
                    mDriver.send();
                } catch( InterruptedIOException ex ) {
                } catch( IOException ex ) {
                    ex.printStackTrace();
                }
            } else {
                try {
                    if( mDriver.queue() ) {
                        sendPacket = true;
                    }
                } catch( EOFException ex ) {
                    synchronized( this ) {
                        mEof = true;
                    }
                } catch( IOException ex ) {
                    waitForSomething( "Read failed.", ex );
                }
            }
        }
    }
    
    
    private synchronized void waitForSomething( String message, Exception ex ) {
        if( ex instanceof EOFException ) {
            try {
                wait( 1000L );
            } catch( InterruptedException e ) {}
        } else {
            sLog.log( Level.WARNING, message, ex );
        }
    }
    
    
    private static void warn( String msg, Exception ex ) {
        sLog.log( Level.WARNING, msg, ex );
    }
    
    
    
    private final class PlayHandler implements PlayControl {

        public void playStart( long execMicros ) {}

        public void playStop( long execMicros ) {}

        public void seek( long execMicros, long seekMicros ) {
            synchronized( RealtimeDriver.this ) {
                mNeedUpdate = true;
                mNeedSeek   = true;
                mSeekMicros = seekMicros;
                RealtimeDriver.this.notifyAll();
                
                
                if( mCanInterrupt ) {
                    mThread.interrupt();
                }
            }
        }

        public void setRate( long execMicros, double rate ) {}
        
    }
    
}
