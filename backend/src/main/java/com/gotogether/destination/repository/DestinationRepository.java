package com.gotogether.destination.repository;

import com.gotogether.destination.entity.Destination;
import com.gotogether.destination.entity.DestinationCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Public only because Spring Data requires it — package-private-in-practice
 * access is enforced by {@code ArchitectureTest}, not Java visibility (see
 * {@code UserRepository}'s doc for the same note).
 */
public interface DestinationRepository extends JpaRepository<Destination, UUID> {

    List<Destination> findByActiveTrueOrderByPopularityRankAscNameAsc();

    List<Destination> findByActiveTrueAndCategoryOrderByPopularityRankAscNameAsc(DestinationCategory category);

    @Query("select d from Destination d where d.active = true and d.popularityRank is not null order by d.popularityRank asc")
    List<Destination> findPopular();

    @Query("select d from Destination d where d.active = true and lower(d.name) like lower(concat('%', :query, '%')) "
            + "order by (case when d.popularityRank is null then 1 else 0 end), d.popularityRank asc, d.name asc")
    List<Destination> search(@Param("query") String query);
}
