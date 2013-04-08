//package cogmac.javdraw;
//
//import java.io.IOException;
//import java.lang.ref.*;
//import java.util.*;
//
//import cogmac.clocks.PlayController;
//import cogmac.data.Guid;
//import cogmac.jav.JavConstants;
//
//
//@SuppressWarnings( { "rawtypes", "unchecked" } )
//public abstract class Panacea {
//
//    private final Source mSource;
//    private final int mVidPoolCap;
//    private final int mAudioPoolCap;
//    
//    private final Map<Guid,RawNode> mRawMap = new HashMap<Guid,RawNode>();
//    
//    private boolean mClosed   = false;
//    private boolean mNeedSeek = false;
//    private long mSeekMicros  = Long.MIN_VALUE;
//    
//    
//    public Panacea( Source source, int vidPoolCap, int audioPoolCap ) {
//        mSource       = source;
//        mVidPoolCap   = vidPoolCap;
//        mAudioPoolCap = audioPoolCap;
//    }
//    
//    
//    public Source source() {
//        return mSource;
//    }
//    
//    public PlayController playController() {
//        return null;
//    }
//
//    public StreamHandle openVideoStream( StreamHandle source,
//                                         PictureFormat destFormat,
//                                         Sink<? super VideoPacket> sink )
//                                         throws IOException
//    {
//        // Check if stream belongs to source.
//        StreamHandle s = mSource.stream( source.guid() );
//        if( s == null || source.type() != JavConstants.AVMEDIA_TYPE_VIDEO ) {
//            return null;
//        }
//        
//        // Resolve dest format.
//        destFormat = PictureFormats.merge( s.pictureFormat(), destFormat );
//        if( destFormat == null || !PictureFormats.isFullyDefined( destFormat ) ) {
//            return null;
//        }
//        
//        return openDest( source,
//                         JavConstants.AVMEDIA_TYPE_VIDEO,
//                         source.pictureFormat(),
//                         destFormat,
//                         sink );
//    }
//        
//
//    public abstract StreamHandle openAudioStream( StreamHandle source,
//                                                  AudioFormat dstFormat,
//                                                  Sink<? super AudioPacket> sink )
//                                                  throws IOException;
//
//    public boolean closeStream( StreamHandle stream ) throws IOException {
//        Sink toClose = null;
//        if( !( stream instanceof DestNode ) ) {
//            return false;
//        }
//        
//        synchronized( this ) {
//            DestNode dest = (DestNode)stream;
//            toClose = SinkCaster.add( toClose, dest.mSink.get() );
//            RawNode raw = dest.mRaw.get();
//            FormatNode format = dest.mFormatter.get();
//            
//            if( format != null ) {
//                toClose = format;
//                
//                
//            }
//            
//            
//            
//            
//        }
//            
//        
//    }
//    
//    public abstract void close() throws IOException;
//    
//    
//    public abstract boolean queue();
//    
//    public abstract long nextMicros();
//    
//    public abstract boolean send();
//    
//    public abstract void seek( long micros );
//    
//    
//    private StreamHandle openDest( StreamHandle sourceStream,
//                                   int type,
//                                   Object sourceFormat,
//                                   Object destFormat,
//                                   Sink sink )
//                                   throws IOException 
//    {
//        boolean newRaw    = false;
//        RawNode raw       = null;
//        boolean newFormat = false;
//        FormatNode format = null;
//        DestNode dest     = null;
//        boolean success   = false;
//        
//        try {
//            synchronized( this ) {
//                raw = mRawMap.get( sourceStream.guid() );
//                if( raw == null ) {
//                    newRaw = true;
//                    raw = new RawNode( sourceStream );
//                }
//
//                format = raw.mFormatterMap.get( destFormat );
//                if( format == null ) {
//                    newFormat = true;
//
//                    if( type == JavConstants.AVMEDIA_TYPE_VIDEO ) {
//                        format = new VideoFormatNode( (PictureFormat)sourceFormat,
//                                (PictureFormat)destFormat );
//                    } else {
//                        format = new AudioFormatNode( (AudioFormat)sourceFormat,
//                                (AudioFormat)destFormat );
//                    }
//                }
//
//                if( type == JavConstants.AVMEDIA_TYPE_VIDEO ) {
//                    dest = new DestNode( type, (PictureFormat)destFormat, null, raw, format, sink );
//                } else {
//                    dest = new DestNode( type, null, (AudioFormat)destFormat, raw, format, sink );
//                }
//
//                if( newRaw ) {
//                    mSource.openStream( sourceStream );
//                }
//                
//                // Nothing can fail after this point.
//                success = true;
//                format.syncAddDest( sink );
//                
//                if( newFormat ) {
//                    raw.syncAddFormat( format );
//                }
//                
//                if( newRaw ) {
//                    mRawMap.put( sourceStream.guid(), raw );
//                }
//                
//                return dest;
//            }
//        } finally {
//            if( !success ) {
//                if( format != null && newFormat ) {
//                    format.close();
//                }
//            }
//        }
//
//    }
//    
//    
//    
//    private static final class RawNode {
//        final StreamHandle mSourceStream;
//        final Map<Object,FormatNode> mFormatterMap = new HashMap<Object,FormatNode>();
//        Sink mFormatters = null;
//        
//        RawNode( StreamHandle source ) {
//            mSourceStream = source;
//        }
//    
//        
//        void syncAddFormat( FormatNode format ) {
//            mFormatterMap.put( format.mKey, format );
//            mFormatters = SinkCaster.add( mFormatters, format );
//        }
//        
//        boolean syncRemoveFormat( FormatNode format ) {
//            boolean ret = mFormatterMap.remove( format.mKey ) != null;
//            mFormatters = SinkCaster.remove( mFormatters, format );
//            return ret;
//        }
//        
//    }
//    
//    
//    private static class FormatNode implements Sink {
//        final Object mKey;
//        Sink mSinks = null;
//        
//        public FormatNode( Object key ) {
//            mKey = key;
//        } 
//
//        
//        
//        public void consume( Object packet ) throws IOException {}
//        
//        
//        public void close() {}
//        
//        
//        public void clear() {}
//        
//        
//        void syncAddDest( Sink sink ) {
//            mSinks = SinkCaster.add( mSinks, sink );
//        }
//        
//        
//        void syncRemoveDest( Sink sink ) {
//            mSinks = SinkCaster.remove( mSinks, sink );
//        }
//       
//    }
//
//    
//    private static class VideoFormatNode extends FormatNode {
//        
//        VideoFormatNode( PictureFormat source, PictureFormat dest ) {
//            super( dest );
//        }
//        
//    }
//    
//    
//    private static class AudioFormatNode extends FormatNode {
//        
//        AudioFormatNode( AudioFormat source, AudioFormat dest ) {
//            super( dest );
//        }
//        
//    }
//    
//    
//    private static final class DestNode extends BasicStreamHandle {
//        
//        Reference<RawNode> mRaw;
//        Reference<FormatNode> mFormatter;
//        Reference<Sink> mSink;
//        
//        public DestNode( int type,
//                         PictureFormat picFormat,
//                         AudioFormat audioFormat,
//                         RawNode raw,
//                         FormatNode formatter,
//                         Sink sink )
//        {
//            super( type, picFormat, audioFormat );
//            mRaw       = new WeakReference( raw );
//            mFormatter = new WeakReference( formatter );
//            mSink      = new WeakReference( sink );
//        }
//
//        
//    }
//    
//    
//}
