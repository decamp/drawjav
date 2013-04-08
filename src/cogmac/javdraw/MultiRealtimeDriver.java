package cogmac.javdraw;

import java.io.*;
import java.util.*;
import java.util.logging.*;

import cogmac.clocks.*;



/**
 * Handles realtime processing of one source.
 * 
 * @author decamp
 */
public class MultiRealtimeDriver implements MultiSourceDriver {
    
    
    public static MultiRealtimeDriver newInstance( PlayController playCont ) {
        return newInstance( playCont, null );
    }
    
    
    public static MultiRealtimeDriver newInstance( PlayController playCont,
                                                   PacketSyncer syncer ) 
    {
        if( syncer == null ) {
            syncer = new PacketSyncer(playCont);
        }    
        
        return new MultiRealtimeDriver( playCont, syncer );
    }

    
    
    private static Logger sLog = Logger.getLogger( MultiRealtimeDriver.class.getName() );
    
    private final PlayController mPlayCont;
    private final PacketSyncer mSyncer;
    
    private final Map<Source,SourceData> mSourceMap       = new HashMap<Source,SourceData>();
    private final Map<StreamHandle,SourceData> mStreamMap = new HashMap<StreamHandle,SourceData>();
    
    private boolean mClosed = false;
    
    
    
    private MultiRealtimeDriver( PlayController playCont,
                                 PacketSyncer syncer )
    {
        mPlayCont    = playCont;
        mSyncer      = syncer;
    }
    
    
    
    
    public PlayController playController() {
        return mPlayCont;
    }
    
    
    public synchronized boolean addSource( Source source ) {
        if( mClosed ) {
            return false;
        }
        if( mSourceMap.containsKey( source ) ) {
            return false;
        }
        SourceData data = new SourceData( source );
        mSourceMap.put( source, data );
        
        for( StreamHandle s: data.mStreams ) {
            mStreamMap.put( s, data );
        }
        
        data.mDriver = RealtimeDriver.newInstance( mPlayCont, source, mSyncer );
        
        return true;   
    }
    
    
    public boolean removeSource(Source source) {
        SourceData data;
        
        synchronized(this) {
            data = mSourceMap.remove(source);
            if(data == null)
                return false;
            
            
            for(StreamHandle h: data.mStreams) {
                mStreamMap.remove(h);
            }
        }
        
        try {
            data.mDriver.close();
        }catch(IOException ex) {
            sLog.log(Level.WARNING, "Failed to close driver.", ex);
        }
        
        return true;
    }
    
    
    public StreamHandle openVideoStream( StreamHandle source,
                                         PictureFormat destFormat,
                                         Sink<? super VideoPacket> sink )
                                         throws IOException 
    {
        StreamDriver driver = null;
        
        synchronized( this ) {
            SourceData data = mSourceMap.get( source );
            if( data == null ) {
                return null;
            }
            driver = data.mDriver;
        }
        
        return driver.openVideoStream( source, destFormat, sink );
    }
    
    
    public StreamHandle openAudioStream( StreamHandle source,
                                         AudioFormat format,
                                         Sink<? super AudioPacket> sink )
                                         throws IOException 
    {
        StreamDriver driver = null;
        
        synchronized(this) {
            SourceData data = mSourceMap.get(source);
            if(data == null)
                return null;
            
            driver = data.mDriver;
        }
        
        return driver.openAudioStream(source, format, sink);
    }

    
    public boolean closeStream(StreamHandle stream) throws IOException {
        StreamDriver driver = null;
        
        synchronized(this) {
            SourceData data = mSourceMap.get(stream);
            if(data == null)
                return false;
            
            driver = data.mDriver;
        }
        
        return driver.closeStream(stream);
    }
    
    
    public void close() {
        List<StreamDriver> drivers;
        
        synchronized(this) {
            if(mClosed)
                return;
            
            mClosed = true;
            drivers = new ArrayList<StreamDriver>(mSourceMap.size());
            
            for(SourceData d: mSourceMap.values()) {
                drivers.add(d.mDriver);
            }
            
            mSourceMap.clear();
            mStreamMap.clear();
        }
        
        for(StreamDriver d: drivers) {
            try {
                d.close();
            }catch(IOException ex) {
                sLog.log(Level.WARNING, "Failed to close driver", ex);
            }
        }
    }
    
    
    public boolean isOpen() {
        return !mClosed;
    }
    
    

    private final class SourceData {
        List<StreamHandle> mStreams;
        StreamDriver mDriver = null;
        
        
        SourceData(Source source) {
            mStreams = source.streams();
        }
    }
    
    
}
