package bits.drawjav;

import java.util.*;

import org.junit.*;
import static org.junit.Assert.*;


/**
 * @author decamp
 */
public class PrioHeapTest {
    
    
    @Test
    public void testOrder() throws Exception {
        PrioHeap<Node> heap = new PrioHeap<Node>( NODE_COMP );
        for( int j = 0; j < 100; j++ ) {
            Random rand = new Random( j );
            for( int i = 0; i < 1000; i++ ) {
                int r = rand.nextInt( 10000 );
                heap.offer( new Node( r ) );
            }

            int prev = Integer.MIN_VALUE;
            
            while( !heap.isEmpty() ) {
                Node n = heap.remove();
                assertTrue( prev <= n.mValue );
            }
        }
    }


    @Test
    public void testRemoveHead() {
        PrioHeap<Node> q = new PrioHeap<Node>( NODE_COMP );
        List<Node> nodes = nodeList( 10 );
        for( Node n: nodes ) {
            q.offer( n );
        }
        
        for( int i = 0; i < nodes.size(); i++ ) {
            q.remove();
            assertEquals( q.size(), nodes.size() - i - 1 );
        }
    }

    
    @Test
    public void testQueue() {
        PrioHeap<Node> q = new PrioHeap<Node>( NODE_COMP );
        List<Node> nodes = nodeList( 10 );
        Random rand = new Random( 1 );
        
        for( Node n: nodes ) {
            q.offer( n );
        }
        check( q, 10 );
        q.remove( nodes.get( 0 ) );
        check( q, 9 );
        q.remove( nodes.get( nodes.size() - 1 ) );
        check( q, 8 );

        for( Node n: nodes ) {
            q.remove( n );
        }
        check( q, 0 );

        for( Node n: nodes ) {
            q.offer( n );
        }
        check( q, 10 );

        for( int i = nodes.size() - 1; i >= 0; i-- ) {
            q.remove( nodes.get( i ) );
        }
        check( q, 0 );

        for( int i = 0; i < 25; i++ ) {
            Collections.shuffle( nodes, rand );
            for( Node n: nodes ) {
                int size = q.size();
                q.offer( n );
                assertEquals( q.size(), size + 1 );
            }
            
            if( i == 0 ) {
                check( q, nodes.size() );
            }
            
            Collections.shuffle( nodes, rand );
            
            for( Node n: nodes ) {
                int size = q.size();
                q.remove( n );
                assertEquals( q.size(), size - 1 );
            }
            if( i == 0 ) {
                check( q, 0 );
            }
        }
    }


    @Test
    public void testDelete() {
        PrioHeap<Node> heap = new PrioHeap<Node>();
        
        for( int j = 0; j < 100; j++ ) {
            Random rand = new Random( j );
            for( int i = 0; i < 1000; i++ ) {
                heap.offer( new Node( rand.nextInt( 10000 ) ) );
            }
            
            for( int i = 0; i < 1000; i++ ) {
                int r = rand.nextInt( heap.size() );
                heap.remove( r );
                check( heap, 1000 - i - 1 );
            }
        }
    }
    
    

    static List<Node> nodeList( int len ) {
        List<Node> ret = new ArrayList<Node>( len );
        for( int i = 0; i < len; i++ ) {
            ret.add( new Node( ( i + 1 ) / 2 ) );
        }
        return ret;
    }


    static void check( PrioHeap<Node> q, int size ) {
        int s = q.size();
        for( int i = s - 1; i > 1; i-- ) {
            Node node   = q.get( i );
            Node parent = q.get( (i-1)>>1 );
            assertTrue( parent.compareTo( node ) <= 0 );
        }
        
        //System.out.println( q );
        assertEquals( q.size(), size );
    }
    


    static class Node extends HeapNode implements Comparable<Node> {
        
        int mValue;
        
        public Node( int value ) {
            mValue = value;
        }
        
        
        public String toString() {
            return "" + mValue;
        }

        
        
        @Override
        public int compareTo( Node b ) {
            return mValue < b.mValue ? -1 :
                   mValue > b.mValue ?  1 :
                   0;
        }

        
        public int hashCode() {
            return mValue;
        }
        
        
        public boolean equals( Object n ) {
            if( !( n instanceof Node ) ) {
                return false;
            }
            return mValue == ((Node)n).mValue;
        }
        
    }

    
    static final Comparator<Node> NODE_COMP = new Comparator<Node>() {
        public int compare( Node a, Node b ) {
            return a.mValue - b.mValue;
        }
    };
    
}
