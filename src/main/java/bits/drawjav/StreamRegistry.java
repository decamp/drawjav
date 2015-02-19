package bits.drawjav;

import java.util.*;


/**
 * @author Philip DeCamp
 */
class StreamRegistry<T> {

    private final Map<PacketReader, Node> mSourceMap = new HashMap<PacketReader, Node>();
    private final Map<StreamHandle, Node> mStreamMap = new HashMap<StreamHandle, Node>();


    public T getSourceData( PacketReader s ) {
        Node n = mSourceMap.get( s );
        return n == null ? null : n.mItem;
    }


    public T getSourceData( StreamHandle s ) {
        Node n = mStreamMap.get( s );
        return n == null ? null : n.mItem;
    }


    public void putSourceData( PacketReader source, T item, boolean putStreams ) {
        Node node = mSourceMap.get( source );
        if( node == null ) {
            node = new Node( source, item );
            mSourceMap.put( source, node );
        }

        if( putStreams ) {
            for( StreamHandle stream: source.streams() ) {
                if( !node.mStreams.contains( stream ) ) {
                    node.mStreams.add( stream );
                    mStreamMap.put( stream, node );
                }
            }
        }
    }


    public boolean addStream( PacketReader source, StreamHandle stream ) {
        Node node = mSourceMap.get( source );
        if( node == null ) {
            return false;
        }
        if( !node.mStreams.contains( stream ) ) {
            node.mStreams.add( stream );
            mStreamMap.put( stream, node );
        }
        return true;
    }


    public List<StreamHandle> getStreamsForSourceRef( PacketReader source ) {
        Node node = mSourceMap.get( source );
        return node == null ? Collections.<StreamHandle>emptyList() : node.mStreams;
    }





    private class Node {
        final PacketReader mSource;
        final List<StreamHandle> mStreams = new ArrayList<StreamHandle>();
        T mItem;

        Node( PacketReader source, T item ) {
            mSource = source;
            mItem = item;
        }
    }

}
