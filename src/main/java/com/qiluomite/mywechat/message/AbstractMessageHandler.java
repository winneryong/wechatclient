package com.qiluomite.mywechat.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blade.kit.DateKit;
import com.blade.kit.StringKit;
import com.blade.kit.http.HttpRequest;
import com.blade.kit.json.JSONArray;
import com.blade.kit.json.JSONObject;
import com.qiluomite.mywechat.bean.WechatMeta;
import com.qiluomite.mywechat.client.RecordContainer;
import com.qiluomite.mywechat.component.Storage;
import com.qiluomite.mywechat.config.Config;
import com.qiluomite.mywechat.config.Constant;

public abstract class AbstractMessageHandler implements IMessageHandler, TxtMessageRobot {

	public static final Logger LOGGER = LoggerFactory.getLogger(AbstractMessageHandler.class);
	protected WechatMeta meta;

	public AbstractMessageHandler(WechatMeta meta) {
		super();
		this.meta = meta;
	}

	public void webwxsendmsg(String content, String to) {

		if (!Config.AUTO_REPLY) {
			LOGGER.warn("auto resply setting was off,please change the Config setting--");
		}
		String url = meta.getBase_uri() + "/webwxsendmsg?lang=zh_CN&pass_ticket=" + meta.getPass_ticket();
		JSONObject body = new JSONObject();
		RecordContainer.cache.add(to);
		String clientMsgId = DateKit.getCurrentUnixTime() + StringKit.getRandomNumber(5);
		JSONObject Msg = new JSONObject();
		Msg.put("Type", 1);
		Msg.put("Content", content);
		Msg.put("FromUserName", meta.getUser().getString("UserName"));
		Msg.put("ToUserName", to);
		Msg.put("LocalID", clientMsgId);
		Msg.put("ClientMsgId", clientMsgId);

		body.put("BaseRequest", meta.getBaseRequest());
		body.put("Msg", Msg);
		HttpRequest request = HttpRequest.post(url).contentType("application/json;charset=utf-8").header("Cookie", meta.getCookie());
		new Thread(() -> {
			try {
				Thread.sleep(3000);
				request.send(body.toString());
				String msgResult = request.body();
				LOGGER.warn("msgResult:{}", msgResult);
				request.disconnect();
			} catch (Exception ex) {
			}
		}).start();
	}

	public String getUserRemarkName(String id) {
		String name = "unknow";
		for (int i = 0, len = Storage.instance().getAllContact().size(); i < len; i++) {
			JSONObject member = Storage.instance().getAllContact().get(i).asJSONObject();
			if (!member.getString("UserName").equals(id)) {
				continue;
			}
			if (StringKit.isNotBlank(member.getString("RemarkName"))) {
				name = member.getString("RemarkName");
			} else {
				name = member.getString("NickName");
			}
			return name;
		}
		return name;
	}

	public String getMemberRemarkName(String chatRoomUserName, String memberUserName) {
		JSONArray chatRoomList = Storage.instance().getLatestChatRoomList();
		for (int i = 0; i < chatRoomList.size(); i++) {
			JSONObject chatroom = chatRoomList.get(i).asJSONObject();
			if (!chatroom.getString("UserName").equals(chatRoomUserName)) {
				continue;
			}
			JSONArray memberList = chatroom.get("MemberList").asArray();
			for (int j = 0; j < memberList.size(); j++) {
				JSONObject member = memberList.get(j).asJSONObject();
				if (member.getString("UserName").equals(memberUserName)) {
					return member.getString("NickName");
				}
			}
		}
		return "未知昵称";

	}

	public boolean preHandle(JSONObject msg) {
		if (Constant.FILTER_USERS.contains(msg.getString("ToUserName"))) {
			LOGGER.info("你收到一条被过滤的消息");
			return false;
		}

		if (isSlefSend(msg)) {
			LOGGER.info(" 您发送了一条消息");
			return false;
		}

		if (msg.getString("FromUserName").indexOf("@@") != -1) {
			LOGGER.info("您收到一条群聊消息");
			return true;
		}

		LOGGER.warn("您收到一条 未知类型消息");
		return true;

	}

	public boolean isSlefSend(JSONObject msg) {
		return msg.getString("FromUserName").equals(this.meta.getUser().getString("UserName"));

	}

	public String getMemberNickName(JSONObject msg) {

		if (this.meta.getUser().getString("UserName").equals(msg.getString("FromUserName"))) {
			return this.meta.getUser().getString("NickName");
		}

		if (isGroupMsg(msg)) {
			String memberUserName = msg.getString("Content").split(":")[0];
			return getMemberRemarkName(msg.getString("FromUserName"), memberUserName);
		} else {
			return getUserRemarkName(msg.getString("FromUserName"));
		}
	}

	public boolean isGroupMsg(JSONObject msg) {
		return msg.getString("FromUserName").startsWith("@@");
	}

	@Override
	public String reply(String uid, String content) {
		return TulingRobot.instance().chat(uid, content);
	}

}
