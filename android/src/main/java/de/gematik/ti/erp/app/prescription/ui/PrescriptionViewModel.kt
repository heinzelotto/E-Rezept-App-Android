/*
 * Copyright (c) 2021 gematik GmbH
 * 
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the Licence);
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 * 
 *     https://joinup.ec.europa.eu/software/page/eupl
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 * 
 */

package de.gematik.ti.erp.app.prescription.ui

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.gematik.ti.erp.app.DispatchProvider
import de.gematik.ti.erp.app.api.Result
import de.gematik.ti.erp.app.api.map
import de.gematik.ti.erp.app.cardwall.usecase.AuthenticationUseCase
import de.gematik.ti.erp.app.common.usecase.HintUseCase
import de.gematik.ti.erp.app.common.usecase.model.CancellableHint
import de.gematik.ti.erp.app.common.usecase.model.Hint
import de.gematik.ti.erp.app.common.usecase.model.PrescriptionScreenHintDefineSecurity
import de.gematik.ti.erp.app.common.usecase.model.PrescriptionScreenHintDemoModeActivated
import de.gematik.ti.erp.app.common.usecase.model.PrescriptionScreenHintNewPrescriptions
import de.gematik.ti.erp.app.common.usecase.model.PrescriptionScreenHintTryDemoMode
import de.gematik.ti.erp.app.core.BaseViewModel
import de.gematik.ti.erp.app.db.entities.SettingsAuthenticationMethod
import de.gematik.ti.erp.app.demo.usecase.DemoUseCase
import de.gematik.ti.erp.app.prescription.ui.model.PrescriptionScreen
import de.gematik.ti.erp.app.prescription.usecase.PollingUseCase
import de.gematik.ti.erp.app.prescription.usecase.PrescriptionUseCase
import de.gematik.ti.erp.app.prescription.usecase.model.PrescriptionUseCaseData
import de.gematik.ti.erp.app.profiles.usecase.ProfilesUseCase
import de.gematik.ti.erp.app.settings.usecase.SettingsUseCase
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class PrescriptionViewModel @Inject constructor(
    private val prescriptionUseCase: PrescriptionUseCase,
    private val profilesUseCase: ProfilesUseCase,
    private val settingsUseCase: SettingsUseCase,
    private val demoUseCase: DemoUseCase,
    private val pollingUseCase: PollingUseCase,
    private val dispatchProvider: DispatchProvider,
    private val hintUseCase: HintUseCase,
    private val authenticationUseCase: AuthenticationUseCase
) : BaseViewModel() {

    val defaultState = PrescriptionScreen.State(
        demoUseCase.isDemoModeActive,
        emptyList(),
        emptyList(),
        emptyList(),
        0
    )

    init {
        viewModelScope.launch {
            pollingUseCase.doRefresh
                .collect {
                    Timber.d("Polling triggered refresh")
                    refreshPrescriptions()
                }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun downloadAllAuditEvents(profileName: String) {
        var result: Result<Any>?
        var count: Int? = null
        var offset: Int? = null
        while (true) {
            result =
                prescriptionUseCase.downloadAuditEvents(
                    profileName = profileName,
                    count = count,
                    offset = offset
                )
            if (result is Result.Success) {
                val nextLink = result.data as String
                if (nextLink.isEmpty()) break
                count = Uri.parse(nextLink).getQueryParameter("_count")?.toInt()
                offset = Uri.parse(nextLink).getQueryParameter("__offset")?.toInt()
            } else {
                break
            }
        }
    }

    @OptIn(FlowPreview::class)
    fun screenState(): Flow<PrescriptionScreen.State> {
        val prescriptionFlow = combine(
            prescriptionUseCase.syncedRecipes(),
            prescriptionUseCase.scannedRecipes(),
        ) { fullDetail, lowDetail ->
            (fullDetail + lowDetail).sortedByDescending {
                when (it) {
                    is PrescriptionUseCaseData.Recipe.Synced -> it.authoredOn
                    is PrescriptionUseCaseData.Recipe.Scanned -> it.scanSessionEnd
                }
            }
        }.onStart {
            emit(emptyList())
        }

        val redeemedPrescription = combine(
            prescriptionUseCase.redeemedSyncedRecipes(),
            prescriptionUseCase.redeemedScannedRecipes(),
        ) { fullDetail, lowDetail ->
            (fullDetail + lowDetail).sortedByDescending {
                when (it) {
                    is PrescriptionUseCaseData.Recipe.Synced -> it.authoredOn
                    is PrescriptionUseCaseData.Recipe.Scanned -> it.scanSessionEnd
                }
            }
        }

        return combine(
            demoUseCase.demoModeActive,
            prescriptionFlow,
            redeemedPrescription,
            settingsUseCase.settings,
            hintUseCase.cancelledHints,
        ) { demoActive, prescriptions, redeemed, settings, cancelledHints ->

            val hints = mutableListOf<Hint>()

            val countOfNewScannedPrescriptions: Int =
                prescriptions.sumOf {
                    when (it) {
                        is PrescriptionUseCaseData.Recipe.Scanned -> it.prescriptions.size
                        is PrescriptionUseCaseData.Recipe.Synced -> 0
                    }
                }

            if (countOfNewScannedPrescriptions != 0) {
                hints += PrescriptionScreenHintNewPrescriptions(countOfNewScannedPrescriptions)
            }

            if (demoActive && PrescriptionScreenHintDemoModeActivated !in cancelledHints) {
                hints += PrescriptionScreenHintDemoModeActivated
            } else if (!demoActive && settings.authenticationMethod == SettingsAuthenticationMethod.Unspecified) {
                hints += PrescriptionScreenHintDefineSecurity
            }
            // TODO: combine any authenticated when hints are gone
            if (!demoUseCase.demoModeHasBeenSeen && PrescriptionScreenHintTryDemoMode !in cancelledHints && !anyProfileAuthenticated()) {
                hints += PrescriptionScreenHintTryDemoMode
            }

            PrescriptionScreen.State(
                showDemoBanner = demoActive,
                hints = hints,
                prescriptions = prescriptions,
                redeemed,
                LocalDate.now().toEpochDay()
            )
        }.flowOn(dispatchProvider.unconfined())
    }

    private suspend fun anyProfileAuthenticated() = withContext(dispatchProvider.io()) {
        profilesUseCase.anyProfileAuthenticated()
    }

    suspend fun refreshPrescriptions() = withContext(dispatchProvider.io()) {
        Timber.d("refreshing Prescriptions")
        val profileName = profilesUseCase.activeProfileName().first()
        prescriptionUseCase.downloadTasks(profileName).map { nrOfNewPrescriptions ->
            if (!demoUseCase.isDemoModeActive) {
                downloadAllAuditEvents(profileName)
                prescriptionUseCase.downloadCommunications(profileName).map {
                    Result.Success(nrOfNewPrescriptions)
                }
            } else {
                Result.Success(nrOfNewPrescriptions)
            }
        }
    }

    fun onCloseHintCard(hint: CancellableHint) {
        hintUseCase.cancelHint(hint)
    }

    suspend fun editScannedPrescriptionsName(
        name: String,
        recipe: PrescriptionUseCaseData.Recipe.Scanned
    ) =
        withContext(dispatchProvider.io()) {
            prescriptionUseCase.editScannedPrescriptionsName(name, recipe.scanSessionEnd)
        }

    fun onAlternateAuthentication() {
        viewModelScope.launch {
            authenticationUseCase.authenticateWithSecureElement()
                .catch {
                    Timber.e(it)
                    cancel("just because")
                }
                .onEach {
                    if (it.isFinal()) {
                        pollingUseCase.refreshNow()
                    }
                }
                .collect()
        }
    }

    fun isCanAvailable() = authenticationUseCase.isCanAvailable()
}
