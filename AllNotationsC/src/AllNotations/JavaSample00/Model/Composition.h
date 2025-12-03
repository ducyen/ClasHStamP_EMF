#ifndef __Composition_H__
#define __Composition_H__
typedef struct tagComposition Composition;
#endif//__Composition_H__
#if !defined( Composition_Init ) && ( defined( __Composition_INTERNAL__ )  )

/** @memberof Composition
 * @brief Composition auto-generated constructor
 */
#define Composition_Init(_attribute4)\
    .attribute4 = _attribute4,\

#define Composition_Ctor( _attribute4 )    ( Composition ){ \
    Composition_Init( P( _attribute4 ) ) \
}
Composition* Composition_Copy( Composition* pComposition, const Composition* pSource );
/** @class Composition
 * @extends 
 */
struct tagComposition{
#define Composition_CLASS                                                                       \
    size_t cbSize;                                                                              \
    Integer attribute4;                                                                                                    \

    Composition_CLASS    
};
#endif//__Composition_INTERNAL__
