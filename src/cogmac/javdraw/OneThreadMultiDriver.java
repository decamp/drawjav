package cogmac.javdraw;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import cogmac.clocks.*;


/**
 * @author decamp
 */
public class OneThreadMultiDriver implements StreamDriver {
    
    
    public static OneThreadMultiDriver newInstance( PlayController playCont ) {
        return newInstance( playCont, null );
    }
    
    
    public static OneThreadMultiDriver newInstance( PlayController playCont,
                                                    PacketSyncer syncer )
    {
        if( syncer == null ) {
            syncer = new PacketSyncer( playCont );
        }
        
        return new OneThreadMultiDriver( playCont, syncer );
    }
    
    
    private static Logger sLog = Logger.getLogger( OneThreadMultiDriver.class.getName() );
    
    
    private final PlayController mPlayCont;
    private final PacketSyncer mSyncer;
    private final PlayHandler mPlayHandler;
    
    private final Map<Source,SourceData> mSourceMap       = new HashMap<Source,SourceData>();
    private final Map<StreamHandle,SourceData> mStreamMap = new HashMap<StreamHandle,SourceData>();
    
    private final Queue<SourceData> mNewSources = new LinkedList<SourceData>();
    private List<SourceData> mSources = new LinkedList<SourceData>();
    
    private Thread mThread;
    
    private boolean mNeedUpdate    = true;
    private boolean mClosed        = false;
    private boolean mCanInterrupt  = false;
    private boolean mNeedSeek      = false;
    private long mSeekMicros       = 0L;
    private long mSeekWarmupMicros = 2000000L;
    
    
    OneThreadMultiDriver( PlayController playCont,
                          PacketSyncer syncer )
    {
        mPlayCont = playCont;
        mSyncer   = syncer;
        
        mPlayHandler = new PlayHandler();
        mPlayCont.caster().addListener( mPlayHandler );
        
        mThread = new Thread( OneThreadMultiDriver.class.getSimpleName() ) {
            public void run() {
                runLoop();
            }
        };
        
        mThread.setDaemon( true );
        mThread.setPriority( Thread.NORM_PRIORITY - 1 );
        mThread.start();
    }
    
    
    
    public boolean isOpen() {
        return !mClosed;
    }
    
    
    public Source source() {
        return null;
    }
    
    
    public PlayController playController() {
        return mPlayCont;
    }
    
    
    public synchronized boolean addSource( Source source ) {
        if( mClosed ) {
            return false;
        }
        if( mSourceMap.containsKey( source ) ) {
            return true;
        }
        
        SourceData data = new SourceData( source );
        data.mDriver.seekWarmupMicros( mSeekWarmupMicros );
        mSourceMap.put( source, data );
        for( int i = 0; i < source.streamCount(); i++ ) {
            mStreamMap.put( source.stream( i ), data );
        }
        return true;
    }
    
    
    public synchronized StreamHandle openVideoStream( StreamHandle stream,
                                                      PictureFormat outputFormat,
                                                      Sink<? super VideoPacket> sink )
                                                      throws IOException 
    {
        SourceData source = mStreamMap.get( stream );
        if( source == null ) {
            return null;
        }

        Sink syncSink = mSyncer.openStream( sink );
        StreamHandle ret = source.mDriver.openVideoStream( stream, outputFormat, sink );
        if( ret == null ) {
            syncSink.close();
            return null;
        }
        
        mNeedUpdate = true;
        
        if( source.mOpenCount++ == 0 ) {
            mNewSources.offer( source );
            notifyAll();
        }

        return ret;
    }
                              

    public synchronized StreamHandle openAudioStream( StreamHandle source, 
                                                      AudioFormat format,
                                                      Sink<? super AudioPacket> sink )
    {
        return null;
    }

    
    public boolean closeStream( StreamHandle stream ) throws IOException {
        return false;
    }
        

    public void close() {
        doDispose();
    }
    
    
    private void runLoop() {
        while( true ) {
            synchronized( this ) {
                mCanInterrupt = false;
                
                if( mNeedUpdate ) {
                    Thread.interrupted();
                    
                    if( mClosed ) {
                        break;
                    }
                    
                    // Check if any work to do.
                    if( !mNewSources.isEmpty() ) {
                        insertSource( mNewSources.remove() );
                        continue;
                    } else if( mSources.isEmpty() ) {
                        try {
                            wait();
                        } catch( InterruptedException ex ) {}
                        continue;
                    }
                    
                    if( mNeedSeek ) {
                        mNeedSeek = false;
                        for( SourceData s: mSources ) {
                            s.mEof = false;
                            s.mNextMicros = Long.MIN_VALUE;
                            s.mDriver.seek( mSeekMicros );
                        }
                        continue;
                    }
                    
                    mNeedUpdate = false;
                }
                
                mCanInterrupt = true;
            }
            
            SourceData s = mSources.remove( 0 );

            if( s.mEof ) {
                // Means all sources are EOF.
                insertSource( s );
                synchronized( this ) {
                    try {
                        wait();
                    } catch( InterruptedException ex ) {}
                    continue;
                }
            }
            
            try {
                if( s.mNextMicros > Long.MIN_VALUE ) {
                    try {
                        s.mNextMicros = Long.MIN_VALUE;
                        s.mDriver.send();
                    } catch( InterruptedIOException ex ) {
                        continue;
                    } catch( IOException ex ) {
                        ex.printStackTrace();
                    }
                }
                
                synchronized( this ) {
                    mCanInterrupt = false;
                    if( mNeedUpdate ) {
                        continue;
                    }
                }
                
                try {
                    while( !mNeedUpdate ) {
                        if( s.mDriver.queue() ) {
                            s.mNextMicros = s.mDriver.nextMicros();
                            break;
                        }
                    } 
                }catch( EOFException ex ) {
                    s.mEof = true;
                } catch( IOException ex ) {
                    s.mEof = true;
                    sLog.log( Level.WARNING, "Error", ex );
                }
            } finally {
                insertSource( s );
            }
        }
    }
    
    
    
    private void insertSource( SourceData source ) {
        ListIterator<SourceData> iter = mSources.listIterator( mSources.size() );
        while( iter.hasPrevious() ) {
            SourceData prev = iter.previous();
            if( prev.mEof != source.mEof ) {
                if( source.mEof ) {
                    iter.next();
                    iter.add( source );
                    return;
                } else {
                    continue;
                }
            }
            
            if( prev.mNextMicros <= source.mNextMicros ) {
                iter.next();
                iter.add( source );
                return;
            }
        }
        
        iter.add( source );
    }
    
    
    private synchronized void doDispose() {
        mThread = null;
        notifyAll();
    }
    
    
    
    private final class PlayHandler implements PlayControl {

        public void playStart( long execMicros ) {}

        public void playStop( long execMicros ) {}

        public void seek( long execMicros, long seekMicros ) {
            synchronized( OneThreadMultiDriver.this ) {
                mNeedUpdate = true;
                mNeedSeek = true;
                mSeekMicros = seekMicros;
                OneThreadMultiDriver.this.notifyAll();
                if( mCanInterrupt && mThread != null ) {
                    mThread.interrupt();
                }
            }
        }

        public void setRate( long execMicros, double rate ) {}
        
    }
    
    
    private static final class SourceData {
        Source mSource;
        PassiveDriver mDriver;
        long mNextMicros = Long.MIN_VALUE;
        boolean mEof     = false;
        int mOpenCount   = 0;
        
        SourceData( Source source ) {
            mSource  = source;
            mDriver  = new PassiveDriver( source );
        }
        
    }
    
    

}
