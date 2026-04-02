package az.tribe.lifeplanner.usecases.health

import az.tribe.lifeplanner.domain.repository.HealthRepository

class SyncHealthDataUseCase(private val repository: HealthRepository) {
    suspend operator fun invoke() {
        repository.syncFromPlatform()
    }
}
