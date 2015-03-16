/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.util.*;

/**
 * Intrinsic priority queue collection. This is "intrinsic" in that it only operates on HeapNode objects,
 * which means this collection does not need to create a node object to wrap each object and can
 * work with zero memory allocation. The downside is that if you add an item to two different heaps,
 * you'll have massive problems.
 * 
 * @author decamp
 */
@SuppressWarnings( {"unchecked", "rawtypes"} )
public class PrioHeap<T extends HeapNode> extends HeapNode {
    
    private final Comparator mComp;
    public HeapNode[] mArr;
    public int mSize = 0;
    
    
    public PrioHeap() {
        this( null );
    }
    
    
    public PrioHeap( Comparator<? super T> comp ) {
        mComp = comp;
        mArr  = new HeapNode[10];
    }
     
    
    
    public void offer( T node ) {
        ensureCapacity( mSize + 1 );
        insertNode( node );
    }

    
    
    public T peek() {
        return mSize > 0 ? (T)mArr[0] : null;
    }
    
    
    public T poll() {
        return mSize == 0 ? null : (T)deleteNode( 0 );
    }
    
    
    public T remove() {
        if( mSize == 0 ) {
            throw new NoSuchElementException();
        }
        return (T)deleteNode( 0 );
    }
    
    
    public T remove( int idx ) {
        if( idx < 0 || idx >= mSize ) {
            throw new NoSuchElementException();
        }
        return (T)deleteNode( idx );
    }
    
    
    public boolean remove( T node ) {
        int idx = node.mHeapIndex;
        if( idx < 0 || idx >= mSize || mArr[idx] != node ) {
            return false;
        }
        
        deleteNode( idx );
        return true;
    }
    
    
    public void reschedule( T node ) {
        int idx = node.mHeapIndex;
        if( idx < 0 || idx >= mSize ) {
            throw new NoSuchElementException();
        }
        
        HeapNode prev = deleteNode( idx );
        if( prev != node ) {
            insertNode( prev );
            throw new NoSuchElementException( "Invalid node index." );
        }
        
        insertNode( node );
    }

    
    public T get( int idx ) {
        return (T)mArr[idx];
    }
    
    
    public void clear() {
        for( int i = 0; i < mSize; i++ ) {
            mArr[i].mHeapIndex = -1;
            mArr[i] = null;
        }
        mSize = 0;
    }
    
    
    public int size() {
        return mSize;
    }

    
    public boolean isEmpty() {
        return mSize == 0;
    }
    
    
    public void ensureCapacity( int minCap ) {
        final int oldCap = mArr.length;
        if( minCap <= oldCap ) {
            return;
        }
        
        int newCap = ( oldCap * 3 ) / 2 + 1;
        if( newCap < minCap ) {
            newCap = minCap;
        }
        
        mArr = Arrays.copyOf( mArr, newCap );
    }
    
    
    @Override
    public String toString() {
        StringBuilder s = new StringBuilder( "Heap: " );
        int start = 0;
        int stop  = Math.min( 1, mSize );
        
        while( start < mSize ) {
            s.append( "\n  " );
            
            for( int i = start; i < stop; i++ ) {
                if( i > start ) {
                    s.append( ", " );
                }
                s.append( mArr[i] );
            }
            
            int len = ( stop - start ) * 2;
            start = stop;
            stop  = Math.min( mSize, start + len );
        }
        
        return s.toString();
    }

    
    
    private int comp( Object a, Object b ) {
        return mComp != null ? mComp.compare( a, b ) : ((Comparable<Object>)a).compareTo( b );
    }

    
    private HeapNode deleteNode( int idx ) {
        int size       = --mSize;
        HeapNode ret   = mArr[idx];
        ret.mHeapIndex = -1;
        HeapNode last  = mArr[size];
        mArr[size] = null;
        
        if( idx == size ) {
            return ret;
        } else if( idx > 0 ) {
            int j = bubbleUp( last, idx );
            if( j != idx ) {
                // Bubble up was succesful.
                mArr[j] = last;
                last.mHeapIndex = j;
                return ret;
            }
        }
        
        // Trickle down.
        int j = bubbleDown( last, idx );
        mArr[j] = last;
        last.mHeapIndex = j;
        
        return ret;
    }

    
    private void insertNode( HeapNode node ) {
        int idx = bubbleUp( node, mSize++ );
        mArr[idx] = node;
        node.mHeapIndex = idx;
    }
    
    
    private int bubbleUp( HeapNode node, int idx ) {
        while( idx > 0 ) {
            int j = (idx-1)>>1;
            HeapNode parent = mArr[j];
            if( comp( parent, node ) <= 0 ) {
                break;
            }
            
            mArr[idx] = parent;
            parent.mHeapIndex = idx;
            idx = j;
        }
        
        return idx;
    }

    
    private int bubbleDown( HeapNode node, int idx ) {
        // Trickle down.
        int j = (idx<<1)+2;
        while( j < mSize ) {
            // Determine the smaller of the two children.
            if( comp( mArr[j-1], mArr[j] ) <= 0 ) {
                j--;
            }
            
            // If node is smaller than smallest child, insert.
            if( comp( node, mArr[j] ) <= 0 ) {
                return idx;
            }
            
            // Move item j->idx.
            HeapNode tmp = mArr[j];
            mArr[idx] = tmp;
            tmp.mHeapIndex = idx;
            
            idx = j;
            j = (j<<1)+2;
        }

        // Special case in which node only has one child.
        if( --j < mSize && comp( node, mArr[j] ) > 0 ) {
            HeapNode tmp = mArr[j];
            mArr[idx] = tmp;
            tmp.mHeapIndex = idx;
            idx = j;
        }
        
        return idx;
    }
    
}
