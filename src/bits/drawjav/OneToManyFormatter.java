package bits.drawjav;

import java.io.IOException;
import java.lang.ref.*;
import java.nio.channels.ClosedChannelException;
import java.util.*;
import java.util.logging.*;

import bits.drawjav.audio.*;
import bits.drawjav.video.*;
import bits.jav.Jav;



/**
 * Handles reformatting of a single stream and 
 * distribution to multiple sinks. MultiOuputFormatter
 * is capable of handling multiple formats, such that
 * a single stream can be formatted in multiple ways.
 * 
 * @author decamp
 *
 */
@SuppressWarnings( { "rawtypes", "unchecked" } )
public class OneToManyFormatter implements Sink<Packet> {

    private static final Logger sLog = Logger.getLogger( OneToManyFormatter.class.getName() );
    
    private final StreamHandle mSource;
    private final Map<Object,FormatNode> mNodeMap = new HashMap<Object,FormatNode>();
    
    private int mVideoPoolCap;
    private int mAudioPoolCap;
    private SinkCaster<Packet> mCaster = new SinkCaster<Packet>();
    private boolean mClosed = false;
    
    
    public OneToManyFormatter( StreamHandle source, 
                                  int videoPoolCap,
                                  int audioPoolCap )
    {
        mSource       = source;
        mVideoPoolCap = videoPoolCap;
        mAudioPoolCap = audioPoolCap;
    }
    
    
    
    public boolean hasSink() {
        return mCaster != null;
    }
    
    
    public synchronized boolean hasSinkOtherThan( StreamHandle handle ) {
        int formats = mNodeMap.size();
        if( formats < 1 ) { 
            return false;
        } else if( formats > 1 ) {
            return true;
        }
        
        // There is at least one output format. Must get more specific.
        if( !( handle instanceof SinkNode ) ) {
            return false;
        }
        
        SinkNode sinkNode = (SinkNode)handle;
        Sink sink = sinkNode.mSink.get();
        if( sink == null ) {
            return true;
        }
        
        FormatNode formatNode = mNodeMap.get( sinkNode.mKey );
        if( formatNode == null ) {
            return true;
        }
        
        return formatNode.mCaster.containsSinkOtherThan( sink );
    }
    
    
    @Override
    public void consume( Packet packet ) throws IOException {
        Sink caster = mCaster;
        if( caster != null ) {
            caster.consume( packet );
        }
    }
    
    
    @Override
    public void clear() {
        Sink caster = mCaster;
        if( caster != null ) {
            caster.clear();
        }
    }

    
    @Override
    public void close() throws IOException {
        Sink caster;
        
        synchronized( this ) {
            if( mClosed ) {
                return;
            }
            mClosed = true;
            caster  = mCaster;
            mCaster = null;
            mNodeMap.clear();
        }
        
        if( caster != null ) {
            caster.close();
        }
    }
    
    
    public boolean isOpen() {
        return !mClosed;
    }
    
    
    public StreamHandle openVideoStream( PictureFormat destFormat, 
                                         Sink<? super VideoPacket> sink )
                                         throws IOException 
    {
        if( mSource.type() != Jav.AVMEDIA_TYPE_VIDEO ) {
            return null;
        }
        
        PictureFormat ret = PictureFormats.merge( mSource.pictureFormat(), destFormat );
        if( ret == null || !PictureFormats.isFullyDefined( ret ) ) {
            return null;
        }
        
        return openStream( Jav.AVMEDIA_TYPE_VIDEO,
                           mSource.pictureFormat(),
                           ret,
                           sink );
    }
    
    
    public StreamHandle openAudioStream( AudioFormat destFormat,
                                         Sink<? super AudioPacket> sink )
                                         throws IOException
    {
        if( mSource.type() != Jav.AVMEDIA_TYPE_AUDIO ) {
            return null;
        }
        
        AudioFormat ret = AudioFormats.merge( mSource.audioFormat(), destFormat );
        if( ret == null || !AudioFormats.isFullyDefined( ret ) ) {
            return null;
        }
        
        return openStream( Jav.AVMEDIA_TYPE_AUDIO,
                           mSource.audioFormat(),
                           destFormat,
                           sink );
    }
    
    
    public boolean closeStream( StreamHandle stream ) {
        if( !( stream instanceof SinkNode ) ) {
            return false;
        }
        
        SinkNode sinkNode     = (SinkNode)stream;
        Sink sink             = sinkNode.mSink.get();
        if( sink == null ) {
            return false;
        }
        
        FormatNode formatNode = null;
        boolean closeFormat   = false;
        boolean closeSink     = false;
        
        synchronized( this ) {
            formatNode = mNodeMap.get( sinkNode.mKey );
            if( formatNode == null ) {
                return false;
            }
            
            closeSink = formatNode.mCaster.removeSink( sink );
            
            if( !formatNode.mCaster.containsSink() ) {
                closeFormat = true;
                removeFormatNode( sinkNode.mKey, formatNode );
            }
        }
        
        if( closeFormat ) {
            try {
                formatNode.mSink.close();
            } catch( IOException ex ) {
                sLog.log( Level.WARNING, "Failed to close converter.", ex ); 
            }
        }
        
        if( closeSink ) {
            try {
                sink.close();
            } catch( IOException ex ) {
                sLog.log( Level.WARNING, "Failed to close sink.", ex );
            }
        }
        
        return closeSink;
    }
    
    
    
    private synchronized StreamHandle openStream( int type, 
                                                  Object sourceFormat,
                                                  Object destFormat,
                                                  Sink dest ) 
                                                  throws IOException
    {
        FormatNode formatNode;
        
        synchronized( this ) {
            if( mClosed ) {
                throw new ClosedChannelException();
            }
            
            formatNode = mNodeMap.get( destFormat );
            
            if( formatNode == null ) {
                SinkCaster caster = new SinkCaster();
                
                if( type == Jav.AVMEDIA_TYPE_VIDEO ) {
                    VideoResamplerPipe conv = new VideoResamplerPipe( caster, 
                                                                      (PictureFormat)sourceFormat,
                                                                      mVideoPoolCap );
                    conv.setPictureConversion( (PictureFormat)destFormat, Jav.SWS_FAST_BILINEAR ); 
                    
                    // As an extra precaution, use the destination format determined
                    // by the video resampler. It should be exactly the same.
                    // If not, we may end up doing redundant conversions, but 
                    // better than breaking.
                    destFormat = conv.destFormat();
                    formatNode = new FormatNode( conv, caster );
                    addNode( destFormat, formatNode );
                    
                } else {
                    AudioResamplerPipe conv = new AudioResamplerPipe( caster, 
                                                                      (AudioFormat)sourceFormat,
                                                                      (AudioFormat)destFormat,
                                                                      mAudioPoolCap );
                    destFormat = conv.destFormat();
                    formatNode = new FormatNode( conv, caster );
                    addNode( destFormat, formatNode );
                }
            }
            
            formatNode.mCaster.addSink( dest );
            
            if( type == Jav.AVMEDIA_TYPE_VIDEO ) {
                return new SinkNode( type, (PictureFormat)destFormat, null, dest );
            } else {
                return new SinkNode( type, null, (AudioFormat)destFormat, dest );
            }
        }
    }
    
    
    private synchronized void addNode( Object key, FormatNode node ) {
        mCaster.addSink( node.mSink );
        mNodeMap.put( key, node );
    }
    
    
    private synchronized void removeFormatNode( Object key, FormatNode node ) {
        mCaster.removeSink( node.mSink );
        mNodeMap.remove( key );
    }
    
        
    
    
    private static class FormatNode<T> {
        
        final Sink<T> mSink;
        final SinkCaster<T> mCaster;
        
        FormatNode( Sink<T> sink, 
                    SinkCaster<T> caster )
        {
            mSink   = sink;
            mCaster = caster;
        }
    }        
    
    
    private static class SinkNode extends BasicStreamHandle {

        final Object mKey;
        final Reference<Sink<?>> mSink;
        
        SinkNode( int type,
                  PictureFormat pictureFormat,
                  AudioFormat audioFormat,
                  Sink<?> sink ) 
        {
            super( type, pictureFormat, audioFormat );
            mKey  = type == Jav.AVMEDIA_TYPE_AUDIO ? audioFormat : pictureFormat;
            mSink = new WeakReference<Sink<?>>( sink );
        }
    }
    
        
}
