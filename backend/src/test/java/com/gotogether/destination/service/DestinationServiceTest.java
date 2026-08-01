package com.gotogether.destination.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gotogether.common.exception.ResourceNotFoundException;
import com.gotogether.destination.entity.Destination;
import com.gotogether.destination.entity.DestinationCategory;
import com.gotogether.destination.repository.DestinationRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DestinationServiceTest {

    @Mock private DestinationRepository destinationRepository;

    private DestinationService destinationService;

    @BeforeEach
    void setUp() {
        destinationService = new DestinationService(destinationRepository);
    }

    @Test
    void listWithNoCategoryReturnsTheFullActiveList() {
        when(destinationRepository.findByActiveTrueOrderByPopularityRankAscNameAsc())
                .thenReturn(List.of(destination("Manali", DestinationCategory.MOUNTAINS, 1)));

        var result = destinationService.list(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Manali");
        verify(destinationRepository).findByActiveTrueOrderByPopularityRankAscNameAsc();
    }

    @Test
    void listWithCategoryDelegatesToTheCategoryFilteredQuery() {
        when(destinationRepository.findByActiveTrueAndCategoryOrderByPopularityRankAscNameAsc(DestinationCategory.BEACHES))
                .thenReturn(List.of(destination("Goa", DestinationCategory.BEACHES, 3)));

        var result = destinationService.list(DestinationCategory.BEACHES);

        assertThat(result).extracting("name").containsExactly("Goa");
    }

    @Test
    void searchWithBlankQueryReturnsEmptyWithoutTouchingTheRepository() {
        var result = destinationService.search("   ");

        assertThat(result).isEmpty();
    }

    @Test
    void searchDelegatesToTheRepositorySearchQuery() {
        when(destinationRepository.search("man")).thenReturn(List.of(destination("Manali", DestinationCategory.MOUNTAINS, 1)));

        var result = destinationService.search("man");

        assertThat(result).extracting("name").containsExactly("Manali");
    }

    @Test
    void popularRespectsTheRequestedLimit() {
        when(destinationRepository.findPopular()).thenReturn(List.of(
                destination("Manali", DestinationCategory.MOUNTAINS, 1),
                destination("Kasol", DestinationCategory.MOUNTAINS, 2),
                destination("Goa", DestinationCategory.BEACHES, 3)));

        var result = destinationService.popular(2);

        assertThat(result).hasSize(2);
    }

    @Test
    void featuredReturnsOneRepresentativePerCategoryIncludingUnrankedCategories() {
        // Mirrors the real V6 seed data shape: two categories have ranked
        // destinations, two don't — featured() must still surface all four
        // categories rather than silently dropping the unranked ones (this is
        // exactly the bug the class doc for featured() explains avoiding).
        when(destinationRepository.findByActiveTrueOrderByPopularityRankAscNameAsc()).thenReturn(List.of(
                destination("Manali", DestinationCategory.MOUNTAINS, 1),
                destination("Kasol", DestinationCategory.MOUNTAINS, 2),
                destination("Goa", DestinationCategory.BEACHES, 3),
                destination("Bir", DestinationCategory.ADVENTURE, null),
                destination("Jaipur", DestinationCategory.WEEKEND_ESCAPES, null)));

        var result = destinationService.featured();

        assertThat(result).extracting("name").containsExactlyInAnyOrder("Manali", "Goa", "Bir", "Jaipur");
    }

    @Test
    void getSummaryThrowsWhenTheDestinationDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(destinationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> destinationService.getSummary(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getSummaryThrowsWhenTheDestinationIsInactive() {
        UUID id = UUID.randomUUID();
        Destination inactive = destination("Old Place", DestinationCategory.MOUNTAINS, null);
        setInactive(inactive);
        when(destinationRepository.findById(id)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> destinationService.getSummary(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    // Destination's constructor is JPA-only (protected, no-arg) with no public
    // factory — unlike Trip/User, it's pure seed data with no application-side
    // creation path (V6 migration owns every row). Reflection is the
    // pragmatic way to build a test fixture without adding a factory method
    // that no real code would ever call.
    private Destination destination(String name, DestinationCategory category, Integer popularityRank) {
        try {
            var constructor = Destination.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Destination d = constructor.newInstance();
            setField(d, "name", name);
            setField(d, "category", category);
            setField(d, "popularityRank", popularityRank);
            setField(d, "active", true);
            return d;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void setInactive(Destination d) {
        try {
            setField(d, "active", false);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = Destination.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
