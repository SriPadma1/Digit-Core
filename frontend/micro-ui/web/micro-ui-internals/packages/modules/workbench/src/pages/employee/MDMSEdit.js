import React,{useState} from 'react'
import MDMSAdd from './MDMSAddV2'
import { Loader,Toast } from '@egovernments/digit-ui-react-components';
import { useTranslation } from "react-i18next";

const MDMSEdit = ({...props}) => {
  const { t } = useTranslation()

  const { moduleName, masterName, tenantId,uniqueIdentifier } = Digit.Hooks.useQueryParams();
  const stateId = Digit.ULBService.getStateId();

  const [showToast, setShowToast] = useState(false);

  const reqCriteria = {
    url: `/mdms-v2/v2/_search`,
    params: {},
    body: {
      MdmsCriteria: {
        tenantId: stateId,
        uniqueIdentifier,
        schemaCodes:[`${moduleName}.${masterName}`]
      },
    },
    config: {
      enabled: moduleName && masterName && true,
      select: (data) => {
        return data?.mdms?.[0]
      },
    },
  };

  const closeToast = () => {
    setTimeout(() => {
      setShowToast(null)
    }, 5000);
  }

  const { isLoading, data, isFetching } = Digit.Hooks.useCustomAPIHook(reqCriteria);


  const reqCriteriaUpdate = {
    url: `/mdms-v2/v2/_update/${moduleName}.${masterName}`,
    params: {},
    body: {
      
    },
    config: {
      enabled: true,
    },
  };
  const mutation = Digit.Hooks.useCustomAPIMutationHook(reqCriteriaUpdate);

  const handleUpdate = async (formData) => {

    const onSuccess = (resp) => {
      
      setShowToast({
        label:`Success : Update Successfull with Id : ${resp?.mdms?.[0]?.id}`
      });
      closeToast()
    };
    const onError = (resp) => {
      setShowToast({
        label:`Error : Following error occured: ${resp?.response?.data?.Errors?.[0]?.description}`,
        isError:true
      });
      
      closeToast()
    };


    mutation.mutate(
      {
        url:`/mdms-v2/v2/_update/${moduleName}.${masterName}`,
        params: {},
        body: {
          Mdms:{
            ...data,
            data:formData
          },
        },
      },
      {
        onError,
        onSuccess,
      }
    );

  }

  if(isLoading) return <Loader />

  return (
    <React.Fragment>
      <MDMSAdd defaultFormData = {data?.data} screenType={"edit"} onSubmitEditAction={handleUpdate}  />
      {showToast && <Toast label={t(showToast.label)} error={showToast?.isError}></Toast>}
    </React.Fragment>
  )
}

export default MDMSEdit