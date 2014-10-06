package bits.drawjav;

import java.io.*;
import java.nio.channels.ClosedChannelException;
import java.util.*;
import java.util.logging.*;

import bits.drawjav.audio.AudioFormat;
import bits.drawjav.audio.AudioPacket;
import bits.drawjav.video.PictureFormat;
import bits.drawjav.video.VideoPacket;
import bits.microtime.*;
import bits.util.concurrent.ThreadLock;


/**
 * @author decamp
 */
@SuppressWarnings( { "unchecked", "rawtypes" } )
public class OneThreadMultiDriver implements MultiSourceDriver {
    
    
    public static OneThreadMultiDriver newInstance( PlayController playCont ) {
        return newInstance( playCont, null );
    }
    
    
    public static OneThreadMultiDriver newInstance( PlayController playCont,
                                                    PacketScheduler scheduler )
    {
        if( scheduler == null ) {
            scheduler = new PacketScheduler( playCont );
        }
        
        return new OneThreadMultiDriver( playCont, scheduler );
    }


    private static Logger sLog = Logger.getLogger( OneThreadMultiDriver.class.getName() );


    private final PlayController  mPlayCont;
    private final ThreadLock      mLock;
    private final PacketScheduler mScheduler;
    private final PlayHandler     mPlayHandler;

    private final Map<Source, SourceData>       mSourceMap = new HashMap<Source, SourceData>();
    private final Map<StreamHandle, SourceData> mStreamMap = new HashMap<StreamHandle, SourceData>();
    private final PrioHeap<SourceData>          mSources   = new PrioHeap<SourceData>();

    private Thread mThread;

    private long mSeekWarmupMicros = 2000000L;
    private int  mVideoQueueCap    = 8;
    private int  mAudioQueueCap    = 16;

    private boolean mClosing = false;

    @SuppressWarnings("unused")
    private boolean mCloseComplete = false;


    OneThreadMultiDriver( PlayController playCont,
                          PacketScheduler syncer )
    {
        mPlayCont = playCont;
        mLock = new ThreadLock();
        mScheduler = syncer;

        mPlayHandler = new PlayHandler();
        mPlayCont.caster().addListener( mPlayHandler );

        mThread = new Thread( OneThreadMultiDriver.class.getSimpleName() ) {
            public void run() {
                runLoop();
            }
        };

        mThread.setDaemon( true );
        mThread.setPriority( Thread.NORM_PRIORITY - 1 );
    }


    public void start() {
        mThread.start();
    }


    public Source source() {
        return null;
    }


    public PlayController playController() {
        return mPlayCont;
    }


    public boolean isOpen() {
        return !mClosing;
    }


    public void close() {
        synchronized( mLock ) {
            if( mClosing ) {
                return;
            }
            mClosing = true;
            mLock.interrupt();
            
            Set<Source> sources = mSourceMap.keySet();
            while( !sources.isEmpty() ) {
                removeSource( sources.iterator().next(), true );
            }
        }
    }
    
    
    public boolean closeSource( Source source ) {
        return removeSource( source, true );
    }    
    
    
    public boolean removeSource( Source source ) {
        return removeSource( source, false );
    }
    
    
    public boolean addSource( Source source ) {
        synchronized( mLock ) {
            if( mClosing ) {
                return false;
            }
            if( mSourceMap.containsKey( source ) ) {
                return true;
            }
            
            SourceData data = new SourceData( source );
            data.mDriver.seekWarmupMicros( mSeekWarmupMicros );
            
            mSourceMap.put( source, data );
            for( StreamHandle s: data.mStreams ) {
                mStreamMap.put( s, data );
            }
            
            mSources.offer( data );
            mLock.unblock();
            return true;
        }
    }
    
    
    public StreamHandle openVideoStream( StreamHandle stream,
                                         PictureFormat outputFormat,
                                         Sink<? super VideoPacket> sink )
                                         throws IOException 
    {
        synchronized( mLock ) {
            if( mClosing ) {
                throw new ClosedChannelException();
            }
            
            SourceData source = mStreamMap.get( stream );
            if( source == null ) {
                return null;
            }
            
            Sink syncSink = mScheduler.openPipe( sink, mLock, mVideoQueueCap );
            StreamHandle ret;
            
            try {
                ret = source.mDriver.openVideoStream( stream, outputFormat, syncSink );
                if( ret == null ) {
                    return null;
                }
                syncSink = null;
            } finally {
                if( syncSink != null ) {
                    syncSink.close();
                }
            }
            
            mSources.reschedule( source );
            mLock.unblock();
            return ret;
        }
    }
                              

    public StreamHandle openAudioStream( StreamHandle stream, 
                                         AudioFormat format,
                                         Sink<? super AudioPacket> sink )
                                         throws IOException 
    {
        synchronized( mLock ) {
            if( mClosing ) {
                throw new ClosedChannelException();
            }
            
            SourceData source = mStreamMap.get( stream );
            if( source == null ) {
                return null;
            }
            
            Sink syncSink = mScheduler.openPipe( sink, mLock, mAudioQueueCap );
            StreamHandle ret;
            
            try {
                ret = source.mDriver.openAudioStream( stream, format, syncSink );
                if( ret == null ) {
                    return null;
                }
                syncSink = null;
            } finally {
                if( syncSink != null ) {
                    syncSink.close();
                }
            }
            
            mSources.reschedule( source );
            mLock.unblock();
            return ret;
        }
            
    }
    
    
    public boolean closeStream( StreamHandle stream ) throws IOException {
        synchronized( mLock ) {
            SourceData source = mStreamMap.get( stream );
            if( source == null ) {
                return false;
            }
            if( !source.mDriver.closeStream( stream ) ) {
                return false;
            }
            mSources.reschedule( source );
            mLock.unblock();
            return true;
        }
    }
        
    
    
    private void runLoop() {
        SourceData s = null;
        boolean sendPacket = false;
        
        while( true ) {
            synchronized( mLock ) {
                if( s != null ) {
                    mSources.reschedule( s );
                }
                
                s = mSources.peek();
                if( s == null ) {
                    // Nothing to do.
                    if( mClosing ) {
                        sLog.fine( "Driver shutdown complete." );
                        mCloseComplete = true;
                        return;
                    }
                    
                    try {
                        mLock.block();
                    } catch( InterruptedIOException ex ) {}
                    
                    continue;
                }
                
                if( !s.mDriver.hasNext() ) {
                    if( !s.mDriver.isOpen() ) {
                        mSources.remove();
                        continue;
                    }
                    
                    try {
                        mLock.block();
                    } catch( InterruptedIOException ex ) {}
                    
                    continue;
                }
                
                sendPacket = s.mDriver.hasCurrent();
            }
            
            if( sendPacket ) {
                s.mDriver.sendCurrent();
            } else {
                s.mDriver.readPacket();
            }
        }
    }
    
    
    
    private boolean removeSource( Source source, boolean closeSource ) {
        synchronized( mLock ) {
            SourceData d = mSourceMap.remove( source );
            if( d == null ) {
                return false;
            }
            for( StreamHandle s: d.mStreams ) {
                mStreamMap.remove( s );
            }
            
            d.mDriver.close( closeSource );
            mLock.unblock();
            return true;
        }
    }
    
    
    
    private final class PlayHandler implements PlayControl {
        
        public void playStart( long execMicros ) {}

        public void playStop( long execMicros ) {}

        public void seek( long execMicros, long seekMicros ) {
            synchronized( mLock ) {
                for( SourceData s: mSourceMap.values() ) {
                    s.mDriver.seek( seekMicros );
                }
                mLock.interrupt();
            }
        }

        public void setRate( long execMicros, double rate ) {}
        
    }
    
    
    private static final class SourceData extends HeapNode implements Comparable<SourceData> {

        final List<StreamHandle> mStreams;
        final PassiveDriver mDriver;
        
        SourceData( Source source ) {
            mStreams = source.streams();
            mDriver  = new PassiveDriver( source );
        }
        
        
        @Override
        public int compareTo( SourceData s ) {
            boolean r0 = mDriver.hasNext();
            boolean r1 = s.mDriver.hasNext();
            
            if( r0 && r1 ) {
                // Both are readable. Sort based on next packet.
                long t0 = mDriver.currentMicros();
                long t1 = s.mDriver.currentMicros();
                return t0 < t1 ? -1 :
                       t0 > t1 ?  1 : 0;
            }
            
            // Schedule closed drivers first.
            boolean close0 = !mDriver.isOpen();
            boolean close1 = !s.mDriver.isOpen();
        
            if( close0 ) {
                return close1 ? 0 : -1;
            } else if( close1 ) {
                return 1;
            }
            
            return r0 ? -1 : ( r1 ? 1 : 0 );
        }
    
    }
    
}
