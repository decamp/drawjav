/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.old;

import java.io.IOException;
import java.lang.ref.*;
import java.nio.channels.ClosedChannelException;
import java.util.*;
import java.util.logging.*;

import bits.drawjav.*;
import bits.jav.Jav;


/**
 * Handles reformatting of a single stream and 
 * distribution to multiple sinks. MultiOuputFormatter
 * is capable of handling multiple formats, such that
 * a single stream can be formatted in multiple ways.
 * 
 * @author decamp
 */
@SuppressWarnings( { "rawtypes", "unchecked" } )
@Deprecated
public class OneToManyFormatter implements Sink<Packet> {

    private static final Logger sLog = Logger.getLogger( OneToManyFormatter.class.getName() );

    private final Stream mStream;
    private final Map<Object, FormatNode> mNodeMap = new HashMap<Object, FormatNode>();

    private MemoryManager mMem;
    private SinkCaster<Packet> mCaster = new SinkCaster<Packet>();
    private boolean            mClosed = false;


    public OneToManyFormatter( Stream stream, MemoryManager mem ) {
        mStream = stream;
        mMem = mem;
    }


    public boolean hasSink() {
        return mCaster != null;
    }


    public synchronized boolean hasSinkOtherThan( Stream handle ) {
        int formats = mNodeMap.size();
        if( formats < 1 ) {
            return false;
        } else if( formats > 1 ) {
            return true;
        }

        // There is at least one output format. Must get more specific.
        if( !(handle instanceof SinkNode) ) {
            return false;
        }

        SinkNode sinkNode = (SinkNode)handle;
        Sink sink = sinkNode.mSink.get();
        if( sink == null ) {
            return true;
        }

        FormatNode formatNode = mNodeMap.get( sinkNode.mKey );
        return formatNode == null || formatNode.mCaster.containsSinkOtherThan( sink );
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


    public MemoryManager memoryManeger() {
        return mMem;
    }


    public void memoryManager( MemoryManager mem ) {
        mMem = mem;
    }

    
    public Stream openVideoStream( StreamFormat destFormat,
                                         Sink<? super DrawPacket> sink )
                                         throws IOException 
    {
        if( mStream.format().mType != Jav.AVMEDIA_TYPE_VIDEO ) {
            return null;
        }
        
        StreamFormat ret = StreamFormat.merge( mStream.format(), destFormat );
        return openStream( Jav.AVMEDIA_TYPE_VIDEO,
                           mStream.format(),
                           ret,
                           sink );
    }
    
    
    public Stream openAudioStream( StreamFormat destFormat,
                                   Sink<? super DrawPacket> sink )
                                   throws IOException
    {
        if( mStream.format().mType != Jav.AVMEDIA_TYPE_AUDIO ) {
            return null;
        }

        StreamFormat ret = StreamFormat.merge( mStream.format(), destFormat );
        return openStream( Jav.AVMEDIA_TYPE_AUDIO,
                           mStream.format(),
                           destFormat,
                           sink );
    }
    
    
    public boolean closeStream( Stream stream ) {
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
                sLog.log( Level.WARNING, "Failed to close input.", ex );
            }
        }
        
        return closeSink;
    }
    
    
    
    private synchronized Stream openStream( int type,
                                            StreamFormat sourceFormat,
                                            StreamFormat destFormat,
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
                    Stream handle = new BasicStream( destFormat );
                    PacketAllocator alloc    = mMem.allocator( handle.format() );
                    VideoResamplerPipe conv = new VideoResamplerPipe( caster, sourceFormat, alloc );
                    alloc.deref();
                    conv.setPictureConversion( destFormat, Jav.SWS_FAST_BILINEAR );
                    // As an extra precaution, use the destination format determined
                    // by the video resampler. It should be the same.
                    // If for some reason it's not, we may end up doing redundant conversions,
                    // but that's better than breaking.
                    destFormat = conv.destFormat();
                    formatNode = new FormatNode( conv, caster );
                    addNode( destFormat, formatNode );
                    
                } else {
                    Stream handle = new BasicStream( destFormat );
                    PacketAllocator alloc = mMem.allocator( handle.format() );
                    AudioResamplerPipe conv = new AudioResamplerPipe( caster, destFormat, alloc );
                    alloc.deref();

                    // As an extra precaution, use the destination format determined
                    // by the audio resampler. It should be the same.
                    // If for some reason it's not, we may end up doing redundant conversions,
                    // but that's better than breaking.
                    destFormat = conv.destFormat();
                    formatNode = new FormatNode( conv, caster );
                    addNode( destFormat, formatNode );
                }
            }
            
            formatNode.mCaster.addSink( dest );
            return new SinkNode( destFormat, dest );
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
    
    
    private static class SinkNode extends BasicStream {

        final Object mKey;
        final Reference<Sink<?>> mSink;
        
        SinkNode( StreamFormat format, Sink<?> sink ) {
            super( format );
            mKey  = format;
            mSink = new WeakReference<Sink<?>>( sink );
        }
    }



    @Deprecated public OneToManyFormatter( Stream source,
                                           int videoPoolCap,
                                           int audioPoolCap )
    {
        mStream = source;
        mMem    = new PoolPerFormatMemoryManager( audioPoolCap, videoPoolCap );
    }
}
