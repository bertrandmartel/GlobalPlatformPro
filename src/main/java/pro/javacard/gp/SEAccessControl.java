/*
 * The MIT License (MIT)
 * <p/>
 * Copyright (c) 2017 Bertrand Martel
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package pro.javacard.gp;

import apdu4j.HexUtils;
import apdu4j.ISO7816;
import com.payneteasy.tlv.*;
import org.bouncycastle.util.Arrays;

import java.util.*;

/**
 * Access control Rules implementation (reference document : Secure Element Access Control Version 1.0).
 *
 * @author Bertrand Martel
 */
public class SEAccessControl {

	public final static AID ACR_AID = new AID("A00000015141434C00");

	public final static byte ACR_GET_DATA_ALL = 0x40;
	public final static byte ACR_GET_DATA_NEXT = 0x60;

	private final static byte[] ACR_GET_DATA_RESP = new byte[]{ (byte)0xFF, (byte)0x40 };

	//Access Rule reference data object (p45 Secure Element Access control spec v1.0)
	private final static byte REF_AR_DO = (byte) 0xE2;
	private final static byte REF_DO = (byte) 0xE1;
	private final static byte AID_REF_DO = (byte) 0x4F;
	private final static byte HASH_REF_DO = (byte) 0xC1;

	private final static byte AR_DO = (byte) 0xE3;
	private final static byte APDU_AR_DO = (byte) 0xD0;
	private final static byte NFC_AR_DO = (byte) 0xD1;

	//from Secure Element Access control spec p46, hash length can be 20 (sha1) or 0
	private final static byte HASH_MAX_LENGTH = (byte) 0x14;
	private final static byte HASH_MIN_LENGTH = (byte) 0x00;

	//command message data object (p38 Secure Element Access control spec v1.0)
	private final static byte STORE_AR_DO = (byte) 0xF0;
	private final static byte DELETE_AR_DO = (byte) 0xF1;

	/**
	 * Store data status work (p44 Secure Element Access control spec v1.0)
	 */
	public final static Map<Integer, String> ACR_STORE_DATA_ERROR;
	static {
		Map<Integer, String> tmp = new HashMap<>();
		tmp.put(0x6381, "Rule successfully stored but an access rule already exists for this target");
		tmp.put(0x6581, "Memory problem");
		tmp.put(ISO7816.SW_WRONG_LENGTH, "Wrong length in Lc");
		tmp.put(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED, "Security status not satisfied");
		tmp.put(ISO7816.SW_CONDITIONS_OF_USE_NOT_SATISFIED, "Conditions not satisfied");
		tmp.put(ISO7816.SW_WRONG_DATA, "Incorrect values in the command data");
		tmp.put(ISO7816.SW_OUT_OF_MEMORY, "Not enough memory space");
		tmp.put(ISO7816.SW_INCORRECT_P1P2, "Incorrect P1 P2");
		tmp.put(ISO7816.SW_KEY_NOT_FOUND, "Referenced data not found");
		tmp.put(0x6A89, "Conflicting access rule already exists in the Secure Element");
		tmp.put(ISO7816.SW_INS_NOT_SUPPORTED, "Invalid instruction");
		tmp.put(ISO7816.SW_CLA_NOT_SUPPORTED, "Invalid class");
		ACR_STORE_DATA_ERROR = Collections.unmodifiableMap(tmp);
	}

	/**
	 * Get Data status word (p27 Secure Element Access control spec v1.0)
	 */
	public final static Map<Integer, String> ACR_GET_DATA_ERROR;
	static {
		Map<Integer, String> tmp = new HashMap<>();
		tmp.put(0x6581, "Memory problem");
		tmp.put(ISO7816.SW_WRONG_LENGTH, "Wrong length in Lc");
		tmp.put(ISO7816.SW_CONDITIONS_OF_USE_NOT_SATISFIED, "Conditions not satisfied");
		tmp.put(ISO7816.SW_WRONG_DATA, "Incorrect values in the command data");
		tmp.put(ISO7816.SW_INCORRECT_P1P2, "Incorrect P1 P2");
		tmp.put(ISO7816.SW_KEY_NOT_FOUND, "Referenced data not found");
		tmp.put(ISO7816.SW_INS_NOT_SUPPORTED, "Invalid instruction");
		tmp.put(ISO7816.SW_CLA_NOT_SUPPORTED, "Invalid class");
		ACR_GET_DATA_ERROR = Collections.unmodifiableMap(tmp);
	}

	/**
	 * Command-Delete-AR-DO (p39) for deleting AID-REF-DO
	 */
	public static class DeleteAidDo implements ITLV {

		AidRefDo aidRefDo;

		public DeleteAidDo(AidRefDo aidRefDo){
			this.aidRefDo = aidRefDo;
		}

		@Override
		public BerTlv toTlv(){
			return new BerTlvBuilder()
					.addBytes(new BerTag(DELETE_AR_DO),  new BerTlvBuilder().addBerTlv(aidRefDo.toTlv()).buildArray())
					.buildTlv();
		}
	}

	/**
	 * Command-Delete-AR-DO (p39) for deleting AR-DO
	 */
	public static class DeleteArDo implements ITLV {

		RefArDo refArDo;

		public DeleteArDo(RefArDo refArDo) {
			this.refArDo = refArDo;
		}

		@Override
		public BerTlv toTlv(){
			return new BerTlvBuilder()
				.addBytes(new BerTag(DELETE_AR_DO), new BerTlvBuilder().addBerTlv(refArDo.toTlv()).buildArray())
				.buildTlv();
		}
	}

	/**
	 * Command-Store-AR-DO (p38)
	 */
	public static class StoreArDo implements ITLV {

		RefArDo refArDo;

		public StoreArDo(RefArDo refArDo){
			this.refArDo = refArDo;
		}

		@Override
		public BerTlv toTlv(){
			return new BerTlvBuilder()
				.addBytes(new BerTag(STORE_AR_DO), new BerTlvBuilder().addBerTlv(refArDo.toTlv()).buildArray())
				.buildTlv();
		}
	}

	/**
	 * REF-AR-DO (p46) composed of REF-DO | AR-DO
	 */
	public static class RefArDo implements ITLV {

		RefDo refDo;
		ArDo arDo;

		public RefArDo(RefDo refDo, ArDo arDo){
			this.refDo = refDo;
			this.arDo = arDo;
		}

		public RefArDo(AID aid, byte[] hash){
			this.refDo = new RefDo(new AidRefDo(aid.getBytes()), new HashRefDo(hash));
			this.arDo = new ArDo(new ApduArDo(EventAccessRules.ALWAYS, new byte[]{}), null);
		}

        public RefArDo(AID aid, byte[] hash, byte[] rules){
			this.refDo = new RefDo(new AidRefDo(aid.getBytes()), new HashRefDo(hash));
			this.arDo = new ArDo(new ApduArDo(rules), null);
		}

		@Override
		public BerTlv toTlv(){
			BerTlvBuilder aggregate =  new BerTlvBuilder()
				.addBerTlv(refDo.toTlv())
				.addBerTlv(arDo.toTlv());
			return new BerTlvBuilder().addBytes(new BerTag(REF_AR_DO),
					aggregate.buildArray()).buildTlv();
		}

		public String toString(){
			return refDo + " | " + arDo;
		}
	}

	interface ITLV {
		BerTlv toTlv();
	}

	/**
	 * REF-DO (p46) composed of AID-REF-DO | Hash-REF-DO
	 */
	public static class RefDo implements ITLV {
		AidRefDo aidRefDo;
		HashRefDo hashRefDo;

		public RefDo(AidRefDo aidRefDo, HashRefDo hashRefDo){
			this.aidRefDo = aidRefDo;
			this.hashRefDo = hashRefDo;
		}

		public String toString(){
			return aidRefDo + " | " + hashRefDo;
		}

		@Override
		public BerTlv toTlv(){
			BerTlvBuilder aggregate = new BerTlvBuilder().addBerTlv(aidRefDo.toTlv()).addBerTlv(hashRefDo.toTlv());
			return new BerTlvBuilder()
				.addBytes(new BerTag(REF_DO), aggregate.buildArray())
				.buildTlv();
		}
	}

	/**
	 * AID-REF-DO data object (p45)
	 */
	public static class AidRefDo implements ITLV {
		byte[] aid;

		public AidRefDo(byte[] data){
			aid = data;
		}

		public String toString(){
			return HexUtils.bin2hex(aid);
		}

		@Override
		public BerTlv toTlv(){
			return new BerTlvBuilder()
					.addBytes(new BerTag(AID_REF_DO), aid)
					.buildTlv();
		}
	}

	/**
	 * Hash-REF-DO (p46)
	 */
	public static class HashRefDo implements ITLV {
		byte[] hash;

		public HashRefDo(byte[] data){
			hash = data;
		}

		public String toString(){
			return HexUtils.bin2hex(hash);
		}

		@Override
		public BerTlv toTlv(){
			return new BerTlvBuilder()
					.addBytes(new BerTag(HASH_REF_DO), hash)
					.buildTlv();
		}
	}

	private static BerTlv buildArDoData(ApduArDo apduArDo, NfcArDo nfcArDo){
        if (apduArDo != null && nfcArDo == null){
            return apduArDo.toTlv();
        }
        if (apduArDo == null && nfcArDo != null){
            return nfcArDo.toTlv();
        }
        if (apduArDo != null && nfcArDo != null) {
            return new BerTlvBuilder().addBerTlv(apduArDo.toTlv()).addBerTlv(nfcArDo.toTlv()).buildTlv();
        }
        return null;
    }

	/**
	 * AR-DO access rule data object (p47) composed of APDU-AR-DO or NFC-AR-DO or APDU-AR-DO | NFC-AR-DO
	 */
	public static class ArDo implements ITLV {

		ApduArDo apduArDo;
		NfcArDo nfcArDo;

		public ArDo(ApduArDo apduArDo, NfcArDo nfcArDo){
            this.apduArDo = apduArDo;
			this.nfcArDo = nfcArDo;
		}

		public String toString(){
			return "apdu : " + apduArDo + " | nfc : " + nfcArDo;
		}

		@Override
		public BerTlv toTlv(){
			return new BerTlvBuilder()
					.addBytes(new BerTag(AR_DO), new BerTlvBuilder().addBerTlv(buildArDoData(apduArDo, nfcArDo)).buildArray())
					.buildTlv();
		}
	}

	private static byte[] buildApduArDoData(EventAccessRules rule, byte[] filter){
        if (rule == EventAccessRules.CUSTOM){
            return filter;
        }
        else if (rule == EventAccessRules.NONE) {
            return new byte[]{};
        }
        else {
            return new byte[]{rule.getValue()};
        }
    }
	/**
	 * APDU-AR-DO access rule data object (p48).
	 */
	public static class ApduArDo implements ITLV {

		EventAccessRules rule;
		byte[] filter;

		public ApduArDo(EventAccessRules rule, byte[] filter){
            this.rule = rule;
			this.filter = filter;
		}

		public ApduArDo(byte[] data){
			if (data != null && data.length == 1){
				switch(data[0]){
					case 0x00:
						this.rule = EventAccessRules.NEVER;
						break;
					case 0x01:
						this.rule = EventAccessRules.ALWAYS;
						break;
					default:
						break;
				}
			}
			else if (data != null){
				this.rule = EventAccessRules.CUSTOM;
				this.filter = new byte[data.length];
			}
			else{
                this.rule = EventAccessRules.NONE;
            }
		}

		@Override
		public BerTlv toTlv(){
			return new BerTlvBuilder()
					.addBytes(new BerTag(APDU_AR_DO), buildApduArDoData(rule, filter))
					.buildTlv();
		}

		public String toString(){
			return "rule : " + rule + " | filter : " + HexUtils.bin2hex(filter);
		}
	}

	/**
	 * NFC-AR-DO access rule data object.
	 */
	public static class NfcArDo implements ITLV {

		EventAccessRules rule;

		public NfcArDo(EventAccessRules rule){
            this.rule = rule;
		}

		@Override
		public BerTlv toTlv(){
			return new BerTlvBuilder()
					.addBytes(new BerTag(NFC_AR_DO), new byte[]{rule.getValue()})
					.buildTlv();
		}

		public String toString(){
			return "rule : " + rule;
		}
	}

	/**
	 * event access rule used by NFC-AR-DO and APDU-AR-DO (p48 + p49)
	 */
	enum EventAccessRules {
		NEVER((byte) 0x00),
		ALWAYS((byte) 0x01),
		CUSTOM((byte) 0x02),
        NONE((byte) 0x03);

		private byte value;

		EventAccessRules(byte value) {
			this.value = value;
		}

		public byte getValue(){
			return value;
		}
	}

	public static class BerTlvData {
		/**
		 * data aggregated from the first get data request.
		 */
		private byte[] data;

		/**
		 * full data length .
		 */
		private int length;

		/**
		 * current processing index.
		 */
		private int currentIndex;

		public BerTlvData(byte[] data, int length, int index){
			this.data = data;
			this.length = length;
			this.currentIndex = index;
		}

		public void setCurrentIndex(int index){
			this.currentIndex = index;
		}

		public byte[] getData(){
			return data;
		}

		public int getLength(){
			return length;
		}

		public int getCurrentIndex(){
			return currentIndex;
		}
	}

	/**
	 * Parse access rule list response.
	 */
	public static class AcrListResponse {

		public List<RefArDo> acrList;

		public AcrListResponse(List<RefArDo> acrList) {
			this.acrList = acrList;
		}

		public static BerTlvData getAcrListData(BerTlvData previousData, byte[] data) throws GPDataException {

			if (previousData == null &&
					data.length > 2 &&
					(data[0] == ACR_GET_DATA_RESP[0]) &&
					(data[1] == ACR_GET_DATA_RESP[1])) {

				int first = data[2] & 0xFF; // fist byte determining length
				int length = 0; // actual length integer
				int offset = 3; //offset

				if (first < 0x80){
					length = first & 0xFF;
				}
				else {
					switch(first) {
						case 0x81:
							length = data[3] & 0xFF;
							offset++;
							break;
						case 0x82:
							length = ((data[3] & 0xFF) << 8) | (data[4] & 0xFF);
							offset+=2;
							break;
						case 0x83:
							length = ((data[3] & 0xFF) << 16) | ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
							offset+=3;
							break;
						default:
							throw new GPDataException("ACR get data : bad BER TLV response format (GET_DATA)");
					}
				}
				byte[] berData = new byte[length];
				System.arraycopy(data, offset, berData, 0, data.length-offset);
				return new BerTlvData(berData, length, data.length - offset);
			}
			else if (previousData != null) {
				System.arraycopy(data, 0, previousData.getData(), previousData.currentIndex, data.length);
				previousData.setCurrentIndex(data.length + previousData.currentIndex);
				return previousData;
			}
			else {
				throw new GPDataException("ACR get data : bad response format (GET_DATA)");
			}
		}

		public static AcrListResponse fromBytes(int length, byte[] data) throws GPDataException {
			BerTlvParser parser = new BerTlvParser();

			List<RefArDo> acrList = new ArrayList<>();

			int offset = 0;
			while (length > offset){
				BerTlvs tlvs = parser.parse(Arrays.copyOfRange(data, offset, data.length));
				BerTlv refArDoTag = tlvs.find(new BerTag(REF_AR_DO));
				acrList.add(parseRefArDo(refArDoTag));
				offset += ((data[1 + offset] & 0xFF) + 2);
			}
			return new AcrListResponse(acrList);
		}
	}

	/**
	 * Parse REF_AR_DO object (p46 Secure Element Access Control v1.0).
	 *
	 * <p>
	 * 0xE2 | length | REF-DO | AR-DO
	 * </p>
	 *
	 * @param refArDo REF_AR_DO data
	 * @return
	 * @throws GPDataException
	 */
	public static RefArDo parseRefArDo(BerTlv refArDo) throws GPDataException {
		RefDo refDo = parseRefDo(refArDo.find(new BerTag(REF_DO)));
		ArDo arDo = parseArDo(refArDo.find(new BerTag(AR_DO)));
		return new RefArDo(refDo, arDo);
	}

	/**
	 * Parse REF_DO object (p46 Secure Element Access control v1.0).
	 *
	 * <p>
	 *	0xE1 | length | AID-REF-DO | Hash-REF-DO
	 * </p>
	 *
	 * @param refDo
	 * @return
	 * @throws GPDataException
	 */
	public static RefDo parseRefDo(BerTlv refDo) throws GPDataException {
        AidRefDo aidRefDo = parseAidRefDo(refDo.find(new BerTag(AID_REF_DO)));
        HashRefDo hashRefDo = parseHashRefDo(refDo.find(new BerTag(HASH_REF_DO)));
        return new RefDo(aidRefDo,hashRefDo);
	}

	/**
	 * Parse AID_REF_DO object (p45 Secure Element Access Control v1.0).
	 *
	 * 4F | length | AID
	 *
	 * @param aidRefDo
	 * @return
	 * @throws GPDataException
	 */
	public static AidRefDo parseAidRefDo(BerTlv aidRefDo) throws GPDataException{
        return new AidRefDo(aidRefDo != null ? aidRefDo.getBytesValue() : new byte[]{});
	}

	/**
	 * Parse HASH_REF_DO (p46 Secure Element Access Control v1.0).
	 *
	 * C1 | length | hash
	 *
	 * @param hashRefDo
	 * @return
	 * @throws GPDataException
	 */
	public static HashRefDo parseHashRefDo(BerTlv hashRefDo) throws GPDataException{
        return new HashRefDo(hashRefDo != null ? hashRefDo.getBytesValue() : new byte[]{});
	}

	/**
	 * Parse AR_DO (p47 Secure Element Access Control v1.0)
	 *
	 * E3 | length | APDU-AR-DO
	 *
	 * OR
	 *
	 * E3 | length | NFC-AR-DO
	 *
	 * OR
	 *
	 * E3 | length | APDU-AR-DO | NFC-AR-DO
	 *
	 * @param arDo
	 * @return
	 * @throws GPDataException
	 */
	public static ArDo parseArDo(BerTlv arDo) throws GPDataException {
		ApduArDo apduArDo = parseApduArDo(arDo.find(new BerTag(APDU_AR_DO)));
		NfcArDo nfcArDo = parseNfcArDo(arDo.find(new BerTag(NFC_AR_DO)));
        return new ArDo(apduArDo, nfcArDo);
	}

	/**
	 * Parse APDU_AR_DO (p48 Secure Element Access Control v1.0).
	 *
	 * D0 | length | 0x00 or 0x01 or APDU filter 1 | APDU filter n
	 *
	 * @param apduArDo
	 * @return
	 * @throws GPDataException
	 */
	public static ApduArDo parseApduArDo(BerTlv apduArDo) throws GPDataException {
		if (apduArDo!=null) {
			byte[] data = apduArDo.getBytesValue();
			if (data.length == 1) {
				switch (data[0] & 0xFF) {
					case 0x01:
						return new ApduArDo(EventAccessRules.ALWAYS, new byte[]{});
					case 0x00:
						return new ApduArDo(EventAccessRules.NEVER, new byte[]{});
				}
			} else {
				return new ApduArDo(EventAccessRules.CUSTOM, data);
			}
		}
		return null;
	}

	/**
	 * Parse NFC_AR_DO (p49 Secure Element Access Control v1.0).
	 *
	 * D1 | 01 | 0x00 or 0x01
	 *
	 * @param nfcArDo
	 * @return
	 * @throws GPDataException
	 */
	public static NfcArDo parseNfcArDo(BerTlv nfcArDo) throws GPDataException{
		if (nfcArDo!=null) {
			switch (nfcArDo.getBytesValue()[0]) {
				case 0x01:
					return new NfcArDo(EventAccessRules.ALWAYS);
				case 0x00:
					return new NfcArDo(EventAccessRules.NEVER);
			}
		}
		return null;
	}

	/**
	 * Print ACR list response.
	 *
	 * @param acrList list of REF-AR-DO
	 */
	public static void printList(List<RefArDo> acrList){
		if (acrList.size() == 0){
			System.out.println("No Rule found");
			return;
		}

		for (int i = 0; i < acrList.size();i++){
			System.out.println("RULE #" + i + " :");
			System.out.println("       AID  : " + acrList.get(i).refDo.aidRefDo);
			System.out.println("       HASH : " + acrList.get(i).refDo.hashRefDo);
			if (acrList.get(i).arDo.apduArDo != null){
				System.out.println("       APDU rule   : " + acrList.get(i).arDo.apduArDo.rule + "(" + String.format("0x%02X" , acrList.get(i).arDo.apduArDo.rule.getValue()) + ")");
				System.out.println("       APDU filter : " + HexUtils.bin2hex(acrList.get(i).arDo.apduArDo.filter));
			}
			if (acrList.get(i).arDo.nfcArDo != null){
				System.out.println("       NFC  rule   : " + acrList.get(i).arDo.nfcArDo.rule + "(" + String.format("0x%02X" , acrList.get(i).arDo.nfcArDo.rule.getValue()) + ")");
			}
		}
	}
}
