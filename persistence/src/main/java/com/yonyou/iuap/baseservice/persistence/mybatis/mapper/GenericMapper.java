package com.yonyou.iuap.baseservice.persistence.mybatis.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.UpdateProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.repository.query.Param;

import com.yonyou.iuap.baseservice.model.Model;
import com.yonyou.iuap.baseservice.persistence.mybatis.ext.template.SqlProvider;
import com.yonyou.iuap.mvc.type.SearchParams;
import com.yonyou.iuap.mybatis.type.PageResult;


/**
 * 说明：基础Mapper
 * @author houlf
 * 2018年6月12日
 */
public interface GenericMapper<T extends Model> {

	public PageResult<T> selectAllByPage(@Param("page") PageRequest pageRequest, @Param("condition") SearchParams searchParams);
	
	public List<T> queryList(@Param("condition")Map<String,Object> params);

	public List<Map<String,Object>> queryListByMap(@Param("condition")Map<String,Object> params);

    @InsertProvider(type=SqlProvider.class, method="insert")  
    public int insert(T entity);
	
    @UpdateProvider(type=SqlProvider.class, method="update")  
	public int update(T entity);

    @DeleteProvider(type=SqlProvider.class, method="delete")  
	public int delete(@Param("condition")Map<String,Object> params);

}