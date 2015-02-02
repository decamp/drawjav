package bits.drawjav;

import java.util.*;


/**
 * @author Philip DeCamp
 */
class StreamRegistry<T> {

    private final Map<Source,Node>       mSourceMap = new HashMap<Source,Node>();
    private final Map<StreamHandle,Node> mStreamMap = new HashMap<StreamHandle,Node>();


    public T getSourceData( Source s ) {
        Node n = mSourceMap.get( s );
        return n == null ? null : n.mItem;
    }


    public T getSourceData( StreamHandle s ) {
        Node n = mStreamMap.get( s );
        return n == null ? null : n.mItem;
    }


    public void putSourceData( Source source, T item, boolean putStreams ) {
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


    public boolean addStream( Source source, StreamHandle stream ) {
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


    public List<StreamHandle> getStreamsForSourceRef( Source source ) {
        Node node = mSourceMap.get( source );
        return node == null ? Collections.<StreamHandle>emptyList() : node.mStreams;
    }





    private class Node {
        final Source mSource;
        final List<StreamHandle> mStreams = new ArrayList<StreamHandle>();
        T mItem;

        Node( Source source, T item ) {
            mSource = source;
            mItem = item;
        }
    }

}
