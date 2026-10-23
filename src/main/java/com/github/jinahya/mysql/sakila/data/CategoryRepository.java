package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Category;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends _Repository<Category, Integer> {

}
