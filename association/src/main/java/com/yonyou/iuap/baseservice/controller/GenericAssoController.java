package com.yonyou.iuap.baseservice.controller;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.yonyou.iuap.base.web.BaseController;
import com.yonyou.iuap.baseservice.entity.Model;
import com.yonyou.iuap.baseservice.entity.annotation.Associative;
import com.yonyou.iuap.baseservice.entity.annotation.Reference;
import com.yonyou.iuap.baseservice.ref.service.RefCommonService;
import com.yonyou.iuap.baseservice.service.GenericService;
import com.yonyou.iuap.baseservice.vo.GenericAssoVo;
import com.yonyou.iuap.mvc.constants.RequestStatusEnum;
import com.yonyou.iuap.mvc.type.JsonResponse;
import com.yonyou.iuap.mvc.type.SearchParams;

import cn.hutool.core.util.ReflectUtil;

/**
 * 说明：基础Controller——仅提供主子表关联特性,单表增删改查请参照GenericExController,GenericController
 * 使用时需要在Entity上增加@Associative注解
 * TODO 级联删除下个版本支持
 * @author leon
 * 2018年7月11日
 */
@SuppressWarnings("all")
public abstract  class GenericAssoController<T extends Model> extends BaseController {
    private Logger log = LoggerFactory.getLogger(GenericAssoController.class);

    @Autowired
    RefCommonService refService;
  

    @RequestMapping(value = "/getAssoVo")
    @ResponseBody
    public Object  getAssoVo(PageRequest pageRequest,
                             SearchParams searchParams){
        Serializable id = MapUtils.getString(searchParams.getSearchMap(), "id");
        if (null==id){ return buildSuccess();}
        T entity = service.findById(id);
        List<T> single= refService.fillListWithRef( Arrays.asList(entity) )  ;
        entity=single.get(0);
        Associative associative= entity.getClass().getAnnotation(Associative.class);
        if (associative==null|| StringUtils.isEmpty(associative.fkName())){
            return buildError("","Nothing got @Associative or without fkName",RequestStatusEnum.FAIL_FIELD);
        }
        GenericAssoVo vo = new GenericAssoVo(entity) ;
        for (Class assoKey:subServices.keySet() ){
            List subList= subServices.get(assoKey).queryList(associative.fkName(),id);
            if ( hasReferrence(assoKey)){
                subList=refService.fillListWithRef(subList);
            }
            String sublistKey = StringUtils.uncapitalize(assoKey.getSimpleName())+"List";
            vo.addList( sublistKey,subList);
        }
        JsonResponse result = this.buildSuccess("entity",vo.getEntity());//保证入参出参结构一致
        result.getDetailMsg().putAll(vo.getSublist());
        return  result;
    }

    @RequestMapping(value = "/SaveAssoVo")
    @ResponseBody
    public Object  saveAssoVo(@RequestBody GenericAssoVo<T> vo){
        Associative annotation= vo.getEntity().getClass().getAnnotation(Associative.class);
        if (annotation==null|| StringUtils.isEmpty(annotation.fkName())){
            return buildError("","Nothing got @Associative or without fkName",RequestStatusEnum.FAIL_FIELD);
        }
        T newEntity = service.save( vo.getEntity());
        for (Class assoKey:subServices.keySet() ){
            String sublistKey = StringUtils.uncapitalize(assoKey.getSimpleName())+"List";
            List<Map> subEntities=vo.getList(sublistKey);
            if ( subEntities !=null && subEntities.size()>0 ){
                for (Map subEntity:subEntities){
                    subEntity.put(annotation.fkName(),newEntity.getId());//外键保存
                    String mj=  JSONObject.toJSONString(subEntity);
                    Model entity = (Model) JSON.parseObject(mj,assoKey,Feature.IgnoreNotMatch);
                    if (entity.getId()!=null&&  subEntity.get("dr")!=null && subEntity.get("dr").toString().equalsIgnoreCase("1")){
                        subServices.get(assoKey).delete(entity.getId());
                    }else
                        subServices.get(assoKey).save(entity);
                }

            }

        }
        return this.buildSuccess(newEntity) ;
    }
    
    @RequestMapping(value = "/dataForPrint", method = RequestMethod.POST)
	@ResponseBody
	public Object getDataForPrint(HttpServletRequest request) {
		String params = request.getParameter("params");
		JSONObject jsonObj = JSON.parseObject(params);
		String id = (String) jsonObj.get("id");
		
		T vo = service.findById(id);
		JSONObject jsonVo = JSONObject.parseObject(JSONObject.toJSON(vo).toString());
		
		JSONObject mainData = new JSONObject();
		JSONObject childData = new JSONObject();
		
		JSONArray mainDataJson = new JSONArray();// 主实体数据
		JSONArray childrenDataJson = new JSONArray();// 第一个子实体数据,多个子表需要多个数组
		
		Set<String> setKey = jsonVo.keySet();
		for(String key : setKey ){
			String value = jsonVo.getString(key);
			mainData.put(key, value);
		}
		mainDataJson.add(mainData);// 主表只有一行
		
		//增加子表的逻辑
		
		JSONObject boAttr = new JSONObject();
		//key：主表业务对象code
		boAttr.put("example_print", mainDataJson);
		//key：子表业务对象code
		boAttr.put("ygdemo_yw_sub", childrenDataJson);
		System.out.println(boAttr.toString());
		return boAttr.toString();
	}

    protected boolean hasReferrence(Class entityClass){
        Field[] fields = ReflectUtil.getFields(entityClass);
        for (Field field : fields) {
            Reference ref = field.getAnnotation(Reference.class);
            if (null != ref) {
                return true;
            }
        }
        return false;
    }


    /************************************************************/
    private Map<Class ,GenericService> subServices = new HashMap<>();
    private GenericService<T> service;

    protected void setService(GenericService<T> genericService) {
        this.service = genericService;
    }
    protected void setSubService(Class entityClass, GenericService subService) {
        subServices.put(entityClass,subService);

    }

}
