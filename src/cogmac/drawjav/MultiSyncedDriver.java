package cogmac.drawjav;

import java.io.*;
import java.util.*;
import java.util.logging.*;

import javax.media.opengl.GL;

import cogmac.clocks.*;
import cogmac.draw3d.nodes.*;



/**
 * Handles realtime processing of one source.
 * 
 * @author decamp
 */
public class MultiSyncedDriver extends DrawNodeAdapter implements MultiSourceDriver {
    
    
    public static MultiSyncedDriver newInstance( PlayController playCont ) {
        return new MultiSyncedDriver( playCont );
    }

    
    
    private static Logger sLog = Logger.getLogger(MultiSyncedDriver.class.getName());
    
    private final PlayController mPlayCont;
    
    private final Map<Source,SourceData> mSourceMap       = new HashMap<Source,SourceData>();
    private final Map<StreamHandle,SourceData> mStreamMap = new HashMap<StreamHandle,SourceData>();
    private List<SourceData> mSources = new ArrayList<SourceData>();
    private long mSeekWarmupMicros = 2000000L;
    private boolean mClosed = false;
    
    
    private MultiSyncedDriver( PlayController playCont ) {
        mPlayCont = playCont;
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
        mSources.add( data );
        
        for( StreamHandle s: data.mStreams ) {
            mStreamMap.put( s, data );
        }
        
        return true;   
    }
    
    
    public boolean removeSource( Source source ) {
        return false;
    }
    
    
    public synchronized StreamHandle openVideoStream( StreamHandle stream,
                                                      PictureFormat destFormat,
                                                      Sink<? super VideoPacket> sink )
                                                      throws IOException 
    {
        SourceData source = mStreamMap.get( stream );
        if( source == null ) {
            return null;
        }
        
        StreamHandle ret = source.mDriver.openVideoStream( stream, destFormat, sink );
        if( ret == null ) {
            return null;
        }
        
        return ret;
    }
    
    
    public StreamHandle openAudioStream( StreamHandle source,
                                         AudioFormat format,
                                         Sink<? super AudioPacket> sink )
                                         throws IOException 
    {
        return null;
    }

    
    public boolean closeStream( StreamHandle stream ) throws IOException {
        return false;
    }
    
    
    public synchronized void close() {
        synchronized( this ) {
            if( mClosed ) {
                return;
            }
            mClosed = true;
            mSourceMap.clear();
            mStreamMap.clear();
        }
    }
    
    
    public boolean isOpen() {
        return !mClosed;
    }
    

    
    @Override
    public void pushDraw( GL gl ) {
        synchronized( this ) {
            for( SourceData s: mSources ) {
                s.mDriver.pushDraw( gl );
            }
        }
    }
    

    private final class SourceData {
        List<StreamHandle> mStreams;
        SyncedDriver mDriver = null;
        
        
        SourceData( Source source ) {
            mStreams = source.streams();
            mDriver  = new SyncedDriver( mPlayCont, source );
        }
    }
    
    
}