package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Country;
import org.springframework.stereotype.Repository;

@Repository
public interface CountryRepository extends _Repository<Country, Integer> {

}
