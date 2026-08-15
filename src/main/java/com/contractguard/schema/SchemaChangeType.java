package com.contractguard.schema;

/** The structural differences the Phase 1 diff engine reports. */
public enum SchemaChangeType {

    FIELD_ADDED,
    FIELD_REMOVED,
    FIELD_TYPE_CHANGED,
    FIELD_OPTIONALITY_CHANGED,
    DEFAULT_VALUE_CHANGED,
    ENUM_SYMBOL_ADDED,
    ENUM_SYMBOL_REMOVED,

    /** Name change of any named type: record, enum or fixed. */
    RECORD_NAME_CHANGED,
    NAMESPACE_CHANGED
}
