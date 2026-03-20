#ifndef __ContextImpl_H__
#define __ContextImpl_H__
#include "Context.h"
typedef struct tagContextImpl ContextImpl;
typedef struct tagE2Params {
    EventParams base;
    long Property1;                                             
}E2Params;
typedef enum tagContextImplEvent {
    ContextImpl_E0,                                             
    ContextImpl_E1,                                             
    ContextImpl_E2,                                             
    ContextImpl_E3,                                             
    ContextImpl_E4,                                             
    ContextImpl_E5,                                             
    ContextImpl_EVENT_NUM
}ContextImpl_EVENT;
const TCHAR* ContextImplEvent_toString( ContextImpl_EVENT value );
BOOL ContextImpl_Start( ContextImpl* pContextImpl );
BOOL ContextImpl_EventProc( ContextImpl* pContextImpl, ContextImpl_EVENT nEventId, void* pEventParams );
void ContextImpl_printTestCases( ContextImpl* pContextImpl, int eventId, void* pParams );
#endif//__ContextImpl_H__
#if !defined( ContextImpl_Init ) && ( defined( __ContextImpl_INTERNAL__ )  || defined( __Composition_INTERNAL__ )  || defined( __Aggregration_INTERNAL__ )  || defined( __EventParams_INTERNAL__ )  || defined( __BaseClass_INTERNAL__ )  )
#define __Context_INTERNAL__
#include "Context.h"
#define __HdStateMachine_INTERNAL__
#include "HdStateMachine.h"
void ContextImpl_checkE1Params( EventParams e );

/** @memberof ContextImpl
 * @brief ContextImpl auto-generated constructor
 */
#define ContextImpl_Init(_derivableAttribute, _publicAttribute, _privateAttribute, _internalAttribute, _readOnlyAttribute, _anAggregation, _aProtectedComposition)\
    Context_Init( P( _derivableAttribute ), P( _publicAttribute ), P( _privateAttribute ), P( _internalAttribute ), P( _readOnlyAttribute ), P( _anAggregation ), P( _aProtectedComposition ) )\
    .vTbl = &gContextImplVtbl,\
    .mainStm = MainStmTop_Init(),\

#define ContextImpl_Ctor( _derivableAttribute, _publicAttribute, _privateAttribute, _internalAttribute, _readOnlyAttribute, _anAggregation, _aProtectedComposition )    ( ContextImpl ){ \
    ContextImpl_Init( P( _derivableAttribute ), P( _publicAttribute ), P( _privateAttribute ), P( _internalAttribute ), P( _readOnlyAttribute ), P( _anAggregation ), P( _aProtectedComposition ) ) \
}
extern const BaseClassVtbl gContextImplVtbl;
Context* ContextImpl_Copy( ContextImpl* pContextImpl, const ContextImpl* pSource );
/** @class ContextImpl
 * @extends Context
 */
struct tagContextImpl{
#define ContextImpl_CLASS                                                                       \
    Context_CLASS                                                                               \
    MainStmTop mainStm;                                         

    ContextImpl_CLASS    
};
/* states' declaration */
#define SubStmTop_S101                          ( 1ULL <<  0 )
#define SubStmTop_SubStmInit                    ( 1ULL <<  1 )
#define SubStmTop_S102                          ( 1ULL <<  2 )
#define SubStmTop_S103                          ( 1ULL <<  3 )
#define SubStmTop_Entry1                        ( 1ULL <<  4 )
#define SubStmTop_Exit1                         ( 1ULL <<  5 )
#define SubStmTop_Entry2                        ( 1ULL <<  6 )
#define SubStmTop_SubStm                        ( SubStmTop_S101 | SubStmTop_SubStmInit | SubStmTop_S102 | SubStmTop_S103 | SubStmTop_Entry1 | SubStmTop_Exit1 | SubStmTop_Entry2 )
    HdStateMachine SubStmHsm;                                   
/** @class SubStmTop
 * @extends HdStateMachine
 */
typedef struct tagSubStmTop {
    HdStateMachine* pParentStm;
    BOOL lastEnteredStateRecovering;
}SubStmTop;
#define SubStmTop_Init() {\
    .pParentStm = NULL,\
    .lastEnteredStateRecovering = FALSE,\
    .SubStmHsm = { HdStateMachine_Init() },\
}
/* states' declaration */
#define MainStmTop_S821                         ( 1ULL <<  0 )
#define MainStmTop_S82Init                      ( 1ULL <<  1 )
#define MainStmTop_S822                         ( 1ULL <<  2 )
#define MainStmTop_S8Rgn1                       ( MainStmTop_S821 | MainStmTop_S82Init | MainStmTop_S822 )
/* states' declaration */
#define MainStmTop_S7121                        ( 1ULL <<  0 )
#define MainStmTop_S7122                        ( 1ULL <<  1 )
#define MainStmTop_S712Init                     ( 1ULL <<  2 )
#define MainStmTop_S71Rgn1                      ( MainStmTop_S7121 | MainStmTop_S7122 | MainStmTop_S712Init )
/* states' declaration */
#define MainStmTop_S1                           ( 1ULL <<  0 )
#define MainStmTop_MainStmInit                  ( 1ULL <<  1 )
#define MainStmTop_S21                          ( 1ULL <<  2 )
#define MainStmTop_S22                          ( 1ULL <<  3 )
#define MainStmTop_S2Init                       ( 1ULL <<  4 )
#define MainStmTop_S2                           ( MainStmTop_S21 | MainStmTop_S22 | MainStmTop_S2Init )
#define MainStmTop_S811                         ( 1ULL <<  5 )
#define MainStmTop_S81Init                      ( 1ULL <<  6 )
#define MainStmTop_S812                         ( 1ULL <<  7 )
#define MainStmTop_S8                           ( MainStmTop_S811 | MainStmTop_S81Init | MainStmTop_S812 )
#define MainStmTop_S7111                        ( 1ULL <<  8 )
#define MainStmTop_S7112                        ( 1ULL <<  9 )
#define MainStmTop_S711Init                     ( 1ULL << 10 )
#define MainStmTop_S71                          ( MainStmTop_S7111 | MainStmTop_S7112 | MainStmTop_S711Init )
#define MainStmTop_S7Init                       ( 1ULL << 11 )
#define MainStmTop_S72                          ( 1ULL << 12 )
#define MainStmTop_S7                           ( MainStmTop_S71 | MainStmTop_S7Init | MainStmTop_S72 )
#define MainStmTop_S6                           ( 1ULL << 13 )
#define MainStmTop_S3                           ( 1ULL << 14 )
#define MainStmTop_S9                           ( 1ULL << 15 )
#define MainStmTop_MainStm                      ( MainStmTop_S1 | MainStmTop_MainStmInit | MainStmTop_S2 | MainStmTop_S8 | MainStmTop_S7 | MainStmTop_S6 | MainStmTop_S3 | MainStmTop_S9 )
    HdStateMachine MainStmHsm;                                  
    uint64_t S2ShallowHist;
    SubStmTop S812Hsm;                                          
    HdStateMachine S8Rgn1Hsm;                                   
    SubStmTop S821Hsm;                                          
    HdStateMachine S71Rgn1Hsm;                                  
    uint64_t S7DeepHist;
    SubStmTop S6Hsm;                                            
    SubStmTop S9Hsm;                                            
/** @class MainStmTop
 * @extends HdStateMachine
 */
typedef struct tagMainStmTop {
    HdStateMachine* pParentStm;
    BOOL lastEnteredStateRecovering;
}MainStmTop;
#define MainStmTop_Init() {\
    .pParentStm = NULL,\
    .lastEnteredStateRecovering = FALSE,\
    .MainStmHsm = { HdStateMachine_Init() },\
    .S2ShallowHist = STATE_UNDEF,\
    .S812Hsm = SubStmTop_Init(),\
    .S8Rgn1Hsm = { HdStateMachine_Init() },\
    .S821Hsm = SubStmTop_Init(),\
    .S71Rgn1Hsm = { HdStateMachine_Init() },\
    .S7DeepHist = STATE_UNDEF,\
    .S6Hsm = SubStmTop_Init(),\
    .S9Hsm = SubStmTop_Init(),\
}
#endif//__ContextImpl_INTERNAL__
