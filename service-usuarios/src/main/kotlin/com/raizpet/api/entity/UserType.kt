package com.raizpet.api.entity

@Suppress("EnumEntryName")
enum class UserType {
    customer, admin, producer;

    val role: String get() = name.uppercase()
}
