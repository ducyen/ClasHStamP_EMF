#ifndef __UsedDependency_H__
#define __UsedDependency_H__
typedef struct tagUsedDependency UsedDependency;
#endif//__UsedDependency_H__
#if !defined( UsedDependency_Init ) && ( defined( __UsedDependency_INTERNAL__ )  )

/** @memberof UsedDependency
 * @brief UsedDependency auto-generated constructor
 */
#define UsedDependency_Init(_attribute6)\
    .attribute6 = _attribute6,\

#define UsedDependency_Ctor( _attribute6 )    ( UsedDependency ){ \
    UsedDependency_Init( P( _attribute6 ) ) \
}
UsedDependency* UsedDependency_Copy( UsedDependency* pUsedDependency, const UsedDependency* pSource );
/** @class UsedDependency
 * @extends 
 */
struct tagUsedDependency{
#define UsedDependency_CLASS                                                                    \
    size_t cbSize;                                                                              \
    Integer attribute6;                                                                                                    \

    UsedDependency_CLASS    
};
#endif//__UsedDependency_INTERNAL__
