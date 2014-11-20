/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.io.*;
import java.util.*;

import bits.draw3d.*;
import bits.drawjav.audio.*;
import bits.drawjav.video.*;
import bits.microtime.*;



/**
 * Handles realtime processing of one source.
 * 
 * @author decamp
 */
public class MultiSyncedDriver extends DrawNodeAdapter implements MultiSourceDriver {

    @Deprecated public static MultiSyncedDriver newInstance( PlayController playCont ) {
        return new MultiSyncedDriver( playCont );
    }

    
    private final PlayController mPlayCont;
    
    private final Map<Source,SourceData> mSourceMap       = new HashMap<Source,SourceData>();
    private final Map<StreamHandle,SourceData> mStreamMap = new HashMap<StreamHandle,SourceData>();
    private List<SourceData> mSources = new ArrayList<SourceData>();
    private long mSeekWarmupMicros = 2000000L;
    private boolean mClosed = false;
    
    
    public MultiSyncedDriver( PlayController playCont ) {
        mPlayCont = playCont;
    }
    
    
    
    public void start() {}
    
    
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
    public void pushDraw( DrawEnv d ) {
        synchronized( this ) {
            for( SourceData s: mSources ) {
                s.mDriver.pushDraw( d );
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
