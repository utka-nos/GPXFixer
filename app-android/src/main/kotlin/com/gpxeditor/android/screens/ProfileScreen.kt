package com.gpxeditor.android.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gpxeditor.shared.data.profile.UserProfileRepository
import com.gpxeditor.shared.domain.profile.HeartRateZones
import com.gpxeditor.shared.domain.profile.PowerZones
import com.gpxeditor.shared.domain.profile.Sex
import com.gpxeditor.shared.domain.profile.UserProfile
import com.gpxeditor.shared.feature.profile.ProfileValidation
import com.gpxeditor.shared.feature.profile.ZoneBoundsError
import com.gpxeditor.shared.feature.profile.ZoneDefaults
import java.util.Calendar

/** Global user profile settings; every field is optional and can be cleared by blanking it. */
@Composable
fun ProfileScreen(
    repository: UserProfileRepository,
    onBackClick: () -> Unit,
) {
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val savedProfile = remember { repository.profile.value }

    var weightText by remember { mutableStateOf(savedProfile.weightKg?.let(::formatWeightKg) ?: "") }
    var sex by remember { mutableStateOf(savedProfile.sex) }
    var birthYearText by remember { mutableStateOf(savedProfile.birthYear?.toString() ?: "") }
    var maxHeartRateText by remember { mutableStateOf("") }
    val heartRateBoundTexts = remember {
        boundTexts(savedProfile.heartRateZones?.upperBoundsBpm, HeartRateZones.BOUNDARY_COUNT)
    }
    var ftpText by remember { mutableStateOf(savedProfile.ftpWatts?.toString() ?: "") }
    val powerBoundTexts = remember {
        boundTexts(savedProfile.powerZones?.upperBoundsWatts, PowerZones.BOUNDARY_COUNT)
    }
    var errors by remember { mutableStateOf(emptyMap<ProfileField, String>()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun save() {
        val result = buildProfile(
            weightText = weightText,
            sex = sex,
            birthYearText = birthYearText,
            heartRateBoundTexts = heartRateBoundTexts,
            ftpText = ftpText,
            powerBoundTexts = powerBoundTexts,
            currentYear = currentYear,
        )
        errors = result.errors
        statusMessage = if (result.errors.isEmpty() && result.profile != null) {
            repository.save(result.profile)
            "Profile saved"
        } else {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row {
            Button(onClick = onBackClick) { Text("Back") }
        }
        Text("Profile", style = MaterialTheme.typography.headlineMedium)

        ProfileNumberField(
            label = "Weight (kg)",
            value = weightText,
            errorMessage = errors[ProfileField.WEIGHT],
            allowDecimals = true,
            onValueChange = { weightText = it },
        )

        Text("Sex", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = sex == null,
                onClick = { sex = null },
                label = { Text("Not set") },
            )
            FilterChip(
                selected = sex == Sex.MALE,
                onClick = { sex = Sex.MALE },
                label = { Text("Male") },
            )
            FilterChip(
                selected = sex == Sex.FEMALE,
                onClick = { sex = Sex.FEMALE },
                label = { Text("Female") },
            )
        }

        ProfileNumberField(
            label = "Birth year",
            value = birthYearText,
            errorMessage = errors[ProfileField.BIRTH_YEAR],
            onValueChange = { birthYearText = it },
        )

        Text("Heart rate zones", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Five zones (Z1–Z5) split by four boundaries; Z5 has no upper limit. " +
                "Leave all boundaries empty to keep zones unset.",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        ProfileNumberField(
            label = "Max heart rate (bpm)",
            value = maxHeartRateText,
            errorMessage = errors[ProfileField.MAX_HEART_RATE],
            onValueChange = { maxHeartRateText = it },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = birthYearText.toIntOrNull()
                    ?.let { ProfileValidation.isValidBirthYear(it, currentYear) } == true,
                onClick = {
                    maxHeartRateText = ZoneDefaults
                        .estimatedMaxHeartRate(birthYearText.toInt(), currentYear)
                        .toString()
                },
            ) {
                Text("Estimate (220 − age)")
            }
            Button(
                enabled = maxHeartRateText.toIntOrNull()
                    ?.let(ProfileValidation::isValidMaxHeartRate) == true,
                onClick = {
                    val defaults = ZoneDefaults.heartRateZonesFromMax(maxHeartRateText.toInt())
                    defaults.upperBoundsBpm.forEachIndexed { index, bound ->
                        heartRateBoundTexts[index] = bound.toString()
                    }
                },
            ) {
                Text("Fill zones from max HR")
            }
        }
        heartRateBoundTexts.forEachIndexed { index, text ->
            ProfileNumberField(
                label = "Z${index + 1} upper bound (bpm)",
                value = text,
                errorMessage = null,
                onValueChange = { heartRateBoundTexts[index] = it },
            )
        }
        errors[ProfileField.HEART_RATE_ZONES]?.let { ErrorText(it) }
        TextButton(onClick = { heartRateBoundTexts.indices.forEach { heartRateBoundTexts[it] = "" } }) {
            Text("Clear heart rate zones")
        }

        Text("Power zones", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Seven Coggan zones (Z1–Z7) split by six boundaries; Z7 has no upper limit. " +
                "Leave all boundaries empty to keep zones unset.",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        ProfileNumberField(
            label = "FTP (W)",
            value = ftpText,
            errorMessage = errors[ProfileField.FTP],
            onValueChange = { ftpText = it },
        )
        Button(
            enabled = ftpText.toIntOrNull()?.let(ProfileValidation::isValidFtp) == true,
            onClick = {
                val defaults = ZoneDefaults.powerZonesFromFtp(ftpText.toInt())
                defaults.upperBoundsWatts.forEachIndexed { index, bound ->
                    powerBoundTexts[index] = bound.toString()
                }
            },
        ) {
            Text("Derive zones from FTP")
        }
        powerBoundTexts.forEachIndexed { index, text ->
            ProfileNumberField(
                label = "Z${index + 1} upper bound (W)",
                value = text,
                errorMessage = null,
                onValueChange = { powerBoundTexts[index] = it },
            )
        }
        errors[ProfileField.POWER_ZONES]?.let { ErrorText(it) }
        TextButton(onClick = { powerBoundTexts.indices.forEach { powerBoundTexts[it] = "" } }) {
            Text("Clear power zones")
        }

        Button(onClick = ::save) { Text("Save") }
        statusMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ProfileNumberField(
    label: String,
    value: String,
    errorMessage: String?,
    onValueChange: (String) -> Unit,
    allowDecimals: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            isError = errorMessage != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (allowDecimals) KeyboardType.Decimal else KeyboardType.Number,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        errorMessage?.let { ErrorText(it) }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

internal enum class ProfileField {
    WEIGHT,
    BIRTH_YEAR,
    MAX_HEART_RATE,
    HEART_RATE_ZONES,
    FTP,
    POWER_ZONES,
}

internal data class ProfileBuildResult(
    val profile: UserProfile?,
    val errors: Map<ProfileField, String>,
)

/** Parses the raw field texts into a profile, collecting one inline error message per field. */
internal fun buildProfile(
    weightText: String,
    sex: Sex?,
    birthYearText: String,
    heartRateBoundTexts: List<String>,
    ftpText: String,
    powerBoundTexts: List<String>,
    currentYear: Int,
): ProfileBuildResult {
    val errors = mutableMapOf<ProfileField, String>()

    val weightKg = weightText.trim().replace(',', '.').takeIf(String::isNotEmpty)?.let { text ->
        val parsed = text.toDoubleOrNull()
        if (parsed == null || !ProfileValidation.isValidWeight(parsed)) {
            errors[ProfileField.WEIGHT] = "Enter a weight between " +
                "${ProfileValidation.MIN_WEIGHT_KG.toInt()} and ${ProfileValidation.MAX_WEIGHT_KG.toInt()} kg"
        }
        parsed
    }

    val birthYear = birthYearText.trim().takeIf(String::isNotEmpty)?.let { text ->
        val parsed = text.toIntOrNull()
        if (parsed == null || !ProfileValidation.isValidBirthYear(parsed, currentYear)) {
            errors[ProfileField.BIRTH_YEAR] =
                "Enter a birth year between ${ProfileValidation.MIN_BIRTH_YEAR} and $currentYear"
        }
        parsed
    }

    val heartRateBounds = parseBounds(
        texts = heartRateBoundTexts,
        field = ProfileField.HEART_RATE_ZONES,
        errors = errors,
        validate = ProfileValidation::validateHeartRateBounds,
        rangeDescription = "${ProfileValidation.MIN_HEART_RATE_BPM}–${ProfileValidation.MAX_HEART_RATE_BPM} bpm",
    )

    val ftpWatts = ftpText.trim().takeIf(String::isNotEmpty)?.let { text ->
        val parsed = text.toIntOrNull()
        if (parsed == null || !ProfileValidation.isValidFtp(parsed)) {
            errors[ProfileField.FTP] = "Enter an FTP between " +
                "${ProfileValidation.MIN_FTP_WATTS} and ${ProfileValidation.MAX_FTP_WATTS} W"
        }
        parsed
    }

    val powerBounds = parseBounds(
        texts = powerBoundTexts,
        field = ProfileField.POWER_ZONES,
        errors = errors,
        validate = ProfileValidation::validatePowerBounds,
        rangeDescription = "${ProfileValidation.MIN_POWER_BOUND_WATTS}–${ProfileValidation.MAX_POWER_BOUND_WATTS} W",
    )

    if (errors.isNotEmpty()) return ProfileBuildResult(profile = null, errors = errors)

    return ProfileBuildResult(
        profile = UserProfile(
            weightKg = weightKg,
            sex = sex,
            birthYear = birthYear,
            heartRateZones = heartRateBounds?.let(::HeartRateZones),
            ftpWatts = ftpWatts,
            powerZones = powerBounds?.let(::PowerZones),
        ),
        errors = emptyMap(),
    )
}

private fun parseBounds(
    texts: List<String>,
    field: ProfileField,
    errors: MutableMap<ProfileField, String>,
    validate: (List<Int>) -> ZoneBoundsError?,
    rangeDescription: String,
): List<Int>? {
    val trimmed = texts.map(String::trim)
    if (trimmed.all(String::isEmpty)) return null
    if (trimmed.any(String::isEmpty)) {
        errors[field] = "Fill in all ${texts.size} boundaries or leave them all empty"
        return null
    }

    val bounds = trimmed.map(String::toIntOrNull)
    if (bounds.any { it == null }) {
        errors[field] = "Boundaries must be whole numbers"
        return null
    }

    val parsed = bounds.filterNotNull()
    when (validate(parsed)) {
        ZoneBoundsError.OUT_OF_RANGE -> errors[field] = "Boundaries must be within $rangeDescription"
        ZoneBoundsError.NOT_ASCENDING -> errors[field] = "Each boundary must be higher than the previous one"
        null -> return parsed
    }
    return null
}

private fun boundTexts(bounds: List<Int>?, count: Int) =
    mutableStateListOf(*Array(count) { index -> bounds?.get(index)?.toString() ?: "" })

private fun formatWeightKg(weightKg: Double): String =
    if (weightKg % 1.0 == 0.0) weightKg.toInt().toString() else weightKg.toString()
