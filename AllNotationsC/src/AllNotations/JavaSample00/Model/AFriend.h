#ifndef __AFriend_H__
#define __AFriend_H__
typedef struct tagAFriend AFriend;
#endif//__AFriend_H__
#if !defined( AFriend_Init ) && ( defined( __AFriend_INTERNAL__ )  )

/** @memberof AFriend
 * @brief AFriend auto-generated constructor
 */
#define AFriend_Init(_attribute3)\
    .attribute3 = _attribute3,\

#define AFriend_Ctor( _attribute3 )    ( AFriend ){ \
    AFriend_Init( P( _attribute3 ) ) \
}
AFriend* AFriend_Copy( AFriend* pAFriend, const AFriend* pSource );
/** @class AFriend
 * @extends 
 */
struct tagAFriend{
#define AFriend_CLASS                                                                           \
    size_t cbSize;                                                                              \
    Integer attribute3;                                                                                                    \

    AFriend_CLASS    
};
#endif//__AFriend_INTERNAL__
