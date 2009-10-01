/* Copyright (C) 2009 Mobile Sorcery AB

This program is free software; you can redistribute it and/or modify it under
the terms of the GNU General Public License, version 2, as published by
the Free Software Foundation.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
for more details.

You should have received a copy of the GNU General Public License
along with this program; see the file COPYING.  If not, write to the Free
Software Foundation, 59 Temple Place - Suite 330, Boston, MA
02111-1307, USA.
*/

#ifndef SET_H
#define SET_H

#include <maassert.h>
#include <kazlib/dict.h>

#ifndef NULL
#define NULL 0
#endif

namespace MAUtil {

//******************************************************************************
// Comparator
//******************************************************************************

template<class T> class Comparator {
public:
	static int Compare(const T& a, const T& b) {
		if(a < b)
			return -1;
		else if(a == b)
			return 0;
		else
			return 1;
	}
};

//******************************************************************************
// Pair
//******************************************************************************

template<class F, class S> struct Pair {
	F first;
	S second;
};

//******************************************************************************
// Set
//******************************************************************************

/** \brief Thin template sorted Set.
*
* The Set is a data storage mechanism that stores unique values.
* The Set is sorted, by a method selectable by the user.
* It has fairly fast insert, erase and lookup operations. (O(log n), specifically.)
* It has an Iterator, which allows you to access all elements in order.
*
* This particular implementation is built on top of kazlib's dictionary system,
* which makes it quite small, even when used with multiple data types.
*/
template<class Key, class Comp=Comparator<Key> >
class Set {
public:
	//typedefs
	/** Internal. */
	struct DictNode : dnode_t {
		DictNode();
		Key key;
	};

	/**
	* An Iterator is bound to a specific Set object.
	* The Iterator can point to a specific element in that Set, or at Set::end(),
	* which is "beyond" the last element of the Set.
	* If the iterator points at Set::end(), attempting to access the element
	* will cause a crash.
	*/
	//TODO: postfix operators
	class Iterator {
	public:
		Key& operator*();
		Key* operator->();
	
		/**
		* Causes the Iterator to point to the next element in the Set to which it is bound.
		* If the Iterator points to Set::end(), this operation will cause a crash.
		*/
		Iterator& operator++();

		/**
		* Causes the Iterator to point to the previous element in the Set to which it is bound.
		* \note If the iterator points to the first node,
		* this operation will cause it to point to Set::end().
		*/
		Iterator& operator--();

		bool operator==(const Iterator&) const;
		bool operator!=(const Iterator&) const;

		Iterator& operator=(const Iterator&);
		Iterator(const Iterator&);
	protected:
		DictNode* mNode;
		dict_t* mDict;
		Iterator(dict_t*);
		friend class Set;
	};

	/**
	* A ConstIterator is just like an ordinary Iterator, except
	* all its methods and return values are const.
	*/
	class ConstIterator {
	public:
		const Key& operator*() const;
		const Key* operator->() const;
	
		ConstIterator& operator++();
		ConstIterator& operator--();

		bool operator==(const ConstIterator&) const;
		bool operator!=(const ConstIterator&) const;

		ConstIterator& operator=(const ConstIterator&);
		ConstIterator(const ConstIterator&);
		ConstIterator(const Iterator&);
	protected:
		const DictNode* mNode;
		const dict_t* mDict;
		ConstIterator(const dict_t*);
		friend class Set;
	};

	//constructors
	/// Constructs an empty Set.
	Set();
	/// Constructs a copy of another Set. All elements are also copied.
	Set(const Set&);
	/// Clears this Set, then copies the other Set to this one.
	Set& operator=(const Set&);
	/// The destructor deletes all elements.
	~Set();

	//methods
	/**
	* Inserts a new value into the Set.
	*
	* Returns a Pair. The Pair's first element is true if the value was indeed inserted.
	* The Pair's second element is an Iterator that points to the element in the Set.
	*
	* An element which compares equal to the new one may already be present in the set;
	* in that case, this operation does nothing, and the iterator returned will point to the old element.
	*/
	Pair<bool, Iterator> insert(const Key&);
	/**
	* Searches the Set for a specified Key. The returned Iterator points to the element matching the Key
	* if one was found, or to Set::end() if not.
	*/
	Iterator find(const Key&);
	ConstIterator find(const Key&) const;
	/**
	* Deletes an element, matching the specified Key, from the Set.
	* Returns true if an element was erased, or false if there was no element matching the Key.
	*/
	bool erase(const Key&);
	/**
	* Returns an Iterator pointing to the first element in the Set.
	*/
	Iterator begin();
	ConstIterator begin() const;
	/**
	* Returns an Iterator pointing to a place beyond the last element of the Set.
	* This Iterator is often used for comparison with other Iterators.
	*/
	Iterator end();
	ConstIterator end() const;
	/**
	* Returns the number of elements in the Set.
	*/
	size_t size() const;
	/**
	* Deletes all elements.
	*/
	void clear();

protected:
	dict_t mDict;

	//******************************************************************************
	// DictAllocator
	//******************************************************************************

#ifdef KAZLIB_OPAQUE_DEBUG
#error Need full definition of dnode_t
#endif

	static dnode_t* alloc(void*) { return new DictNode; }
	static void free(dnode_t* node, void*) { delete (DictNode*)node; }
	static int compare(const void*, const void*);

	void init();
};

#include "Set_impl.h"

}	//MAUtil

#endif	//SET_H
