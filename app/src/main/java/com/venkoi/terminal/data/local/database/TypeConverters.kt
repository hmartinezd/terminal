package com.venkoi.terminal.data.local.database

import androidx.room.TypeConverter
import com.venkoi.terminal.core.Money
import com.venkoi.terminal.core.RestaurantId
import com.venkoi.terminal.core.SaleId
import com.venkoi.terminal.core.TerminalId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency

class TerminalTypeConverters {
    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }

    @TypeConverter
    fun fromMoney(value: Money?): String? = value?.amount?.toPlainString()

    @TypeConverter
    fun toMoney(value: String?): Money? = value?.let { Money(it) }

    @TypeConverter
    fun fromInstant(value: Instant?): String? = value?.toString()

    @TypeConverter
    fun toInstant(value: String?): Instant? = value?.let { Instant.parse(it) }

    @TypeConverter
    fun fromTerminalId(id: TerminalId?): String? = id?.value

    @TypeConverter
    fun toTerminalId(value: String?): TerminalId? = value?.let { TerminalId(it) }

    @TypeConverter
    fun fromRestaurantId(id: RestaurantId?): String? = id?.value

    @TypeConverter
    fun toRestaurantId(value: String?): RestaurantId? = value?.let { RestaurantId(it) }

    @TypeConverter
    fun fromSaleId(id: SaleId?): String? = id?.value

    @TypeConverter
    fun toSaleId(value: String?): SaleId? = value?.let { SaleId(it) }

    @TypeConverter
    fun fromZoneId(value: ZoneId?): String? = value?.id

    @TypeConverter
    fun toZoneId(value: String?): ZoneId? = value?.let { ZoneId.of(it) }

    @TypeConverter
    fun fromCurrency(value: Currency?): String? = value?.currencyCode

    @TypeConverter
    fun toCurrency(value: String?): Currency? = value?.let { Currency.getInstance(it) }

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it) }

    @TypeConverter
    fun fromLineId(id: com.venkoi.terminal.core.LineId?): String? = id?.value

    @TypeConverter
    fun toLineId(value: String?): com.venkoi.terminal.core.LineId? = value?.let { com.venkoi.terminal.core.LineId(it) }

    @TypeConverter
    fun fromPricingMode(value: com.venkoi.terminal.domain.model.PricingMode?): String? = value?.name

    @TypeConverter
    fun toPricingMode(value: String?): com.venkoi.terminal.domain.model.PricingMode? = value?.let { com.venkoi.terminal.domain.model.PricingMode.valueOf(it) }

    @TypeConverter
    fun fromCashDiscountMode(value: com.venkoi.terminal.domain.model.CashDiscountMode?): String? = value?.name

    @TypeConverter
    fun toCashDiscountMode(value: String?): com.venkoi.terminal.domain.model.CashDiscountMode? = value?.let { com.venkoi.terminal.domain.model.CashDiscountMode.valueOf(it) }

    @TypeConverter
    fun fromOrderStatus(value: com.venkoi.terminal.domain.model.OrderStatus?): String? = value?.name

    @TypeConverter
    fun toOrderStatus(value: String?): com.venkoi.terminal.domain.model.OrderStatus? = value?.let { com.venkoi.terminal.domain.model.OrderStatus.valueOf(it) }
}
