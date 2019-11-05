package com.avob.openadr.server.oadr20b.vtn.service.ei;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import com.avob.openadr.model.oadr20b.Oadr20bFactory;
import com.avob.openadr.model.oadr20b.Oadr20bJAXBContext;
import com.avob.openadr.model.oadr20b.builders.Oadr20bResponseBuilders;
import com.avob.openadr.model.oadr20b.ei.EiResponseType;
import com.avob.openadr.model.oadr20b.errorcodes.Oadr20bApplicationLayerErrorCode;
import com.avob.openadr.model.oadr20b.exception.Oadr20bMarshalException;
import com.avob.openadr.model.oadr20b.exception.Oadr20bUnmarshalException;
import com.avob.openadr.model.oadr20b.exception.Oadr20bXMLSignatureException;
import com.avob.openadr.model.oadr20b.exception.Oadr20bXMLSignatureValidationException;
import com.avob.openadr.model.oadr20b.oadr.OadrPayload;
import com.avob.openadr.model.oadr20b.oadr.OadrResponseType;
import com.avob.openadr.server.common.vtn.models.ven.Ven;
import com.avob.openadr.server.common.vtn.service.VenService;
import com.avob.openadr.server.oadr20b.vtn.service.XmlSignatureService;

@Service
public class Oadr20bVTNPayloadService {

	@Resource
	private VenService venService;

	@Resource
	private Oadr20bJAXBContext oadr20bJAXBContext;

	@Resource
	private XmlSignatureService xmlSignatureService;

	@Resource
	private Oadr20bVTNEiEventService oadr20bVTNEiEventService;

	@Resource
	private Oadr20bVTNEiRegisterPartyService oadr20bVTNEiRegisterPartyService;

	@Resource
	private Oadr20bVTNEiReportService oadr20bVTNEiReportService;

	@Resource
	private Oadr20bVTNEiOptService oadr20bVTNEiOptService;

	@Resource
	private Oadr20bVTNOadrPollService oadr20bVTNOadrPollService;

	private class UnmarshalledPayload {
		public Object payload;
		public boolean signed;
	}

	private UnmarshalledPayload wrapper(Object unsignedPayload, boolean signed) {
		UnmarshalledPayload unmarshalledPayload = new UnmarshalledPayload();
		unmarshalledPayload.payload = unsignedPayload;
		unmarshalledPayload.signed = signed;
		return unmarshalledPayload;
	}

	private UnmarshalledPayload unmarshall(Ven ven, String payload)
			throws Oadr20bUnmarshalException, Oadr20bXMLSignatureValidationException {

		Object unmarshal = oadr20bJAXBContext.unmarshal(payload);
		Object unsignedPayload = null;
		boolean signed = false;
		if (unmarshal instanceof OadrPayload) {
			OadrPayload oadrPayload = (OadrPayload) unmarshal;
			xmlSignatureService.validate(payload, oadrPayload);
			unsignedPayload = Oadr20bFactory.getSignedObjectFromOadrPayload(oadrPayload);
			signed = true;
		} else {
			unsignedPayload = unmarshal;
		}
		if (ven.getXmlSignature() != null && ven.getXmlSignature() && !signed) {
			EiResponseType xmlSignatureRequiredButAbsent = Oadr20bResponseBuilders
					.newOadr20bEiResponseXmlSignatureRequiredButAbsentBuilder("", ven.getUsername());
			OadrResponseType build = Oadr20bResponseBuilders
					.newOadr20bResponseBuilder(xmlSignatureRequiredButAbsent, ven.getUsername()).build();
			return wrapper(build, signed);
		}

		return wrapper(unsignedPayload, signed);
	}

	private String marshall(Object payload, boolean signed) {
		try {
			if (signed) {
				return xmlSignatureService.sign(payload);

			} else {
				return oadr20bJAXBContext.marshalRoot(payload);
			}
		} catch (Oadr20bXMLSignatureException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (Oadr20bMarshalException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	private String signatureError(Ven ven) {
		boolean signed = (ven != null && ven.getXmlSignature() != null) ? ven.getXmlSignature() : false;
		OadrResponseType response = Oadr20bResponseBuilders
				.newOadr20bResponseBuilder("0", Oadr20bApplicationLayerErrorCode.INVALID_DATA_454, ven.getUsername())
				.withDescription("Can't validate payload xml signature").build();
		return marshall(response, signed);
	}

	private String unmarshallError(Ven ven) {
		boolean signed = (ven != null && ven.getXmlSignature() != null) ? ven.getXmlSignature() : false;
		OadrResponseType response = Oadr20bResponseBuilders
				.newOadr20bResponseBuilder("0", Oadr20bApplicationLayerErrorCode.NOT_RECOGNIZED_453, ven.getUsername())
				.withDescription("Can't unmarshall payload").build();
		return marshall(response, signed);
	}

	public String event(String username, String payload) {
		Ven ven = venService.findOneByUsername(username);
		UnmarshalledPayload unsignedPayload;
		try {
			unsignedPayload = unmarshall(ven, payload);
		} catch (Oadr20bUnmarshalException e) {
			return unmarshallError(ven);
		} catch (Oadr20bXMLSignatureValidationException e) {
			return signatureError(ven);
		}

		Object request = oadr20bVTNEiEventService.request(ven, unsignedPayload.payload);
		return marshall(request, unsignedPayload.signed);

	}

	public String registerParty(String username, String payload) {
		Ven ven = venService.findOneByUsername(username);
		UnmarshalledPayload unsignedPayload;
		try {
			unsignedPayload = unmarshall(ven, payload);
		} catch (Oadr20bUnmarshalException e) {
			return unmarshallError(ven);
		} catch (Oadr20bXMLSignatureValidationException e) {
			return signatureError(ven);
		}
		Object request = oadr20bVTNEiRegisterPartyService.request(ven, unsignedPayload.payload);
		return marshall(request, unsignedPayload.signed);
	}

	public String opt(String username, String payload) {
		Ven ven = venService.findOneByUsername(username);
		UnmarshalledPayload unsignedPayload;
		try {
			unsignedPayload = unmarshall(ven, payload);
		} catch (Oadr20bUnmarshalException e) {
			return unmarshallError(ven);
		} catch (Oadr20bXMLSignatureValidationException e) {
			return signatureError(ven);
		}
		Object request = oadr20bVTNEiOptService.request(ven, unsignedPayload.payload);
		return marshall(request, unsignedPayload.signed);
	}

	public String report(String username, String payload) {
		Ven ven = venService.findOneByUsername(username);
		UnmarshalledPayload unsignedPayload;
		try {
			unsignedPayload = unmarshall(ven, payload);
		} catch (Oadr20bUnmarshalException e) {
			return unmarshallError(ven);
		} catch (Oadr20bXMLSignatureValidationException e) {
			return signatureError(ven);
		}
		Object request = oadr20bVTNEiReportService.request(ven, unsignedPayload.payload);
		return marshall(request, unsignedPayload.signed);
	}

	public String poll(String username, String payload) {
		Ven ven = venService.findOneByUsername(username);
		UnmarshalledPayload unsignedPayload;
		try {
			unsignedPayload = unmarshall(ven, payload);
		} catch (Oadr20bUnmarshalException e) {
			return unmarshallError(ven);
		} catch (Oadr20bXMLSignatureValidationException e) {
			return signatureError(ven);
		}
		Object request = oadr20bVTNOadrPollService.request(ven, unsignedPayload.payload);
		return marshall(request, unsignedPayload.signed);
	}
}
