var loadingVotes = false
var loadingChats = false
var voteStamp = "0"
var chatStamp = "0"

function getSearchParameters() {
	var prmstr = window.location.search.substr(1);
	return prmstr != null && prmstr != "" ? transformToAssocArray(prmstr) : {};
}

function transformToAssocArray(prmstr) {
	var params = {};
	var prmarr = prmstr.split("&");
	for (var i = 0; i < prmarr.length; i++) {
		var tmparr = prmarr[i].split("=");
		params[tmparr[0]] = tmparr[1];
	}
	return params;
}

function getCookie(c_name) {
	var i, x, y, ARRcookies = document.cookie.split(";");
	for (i = 0; i < ARRcookies.length; i++) {
		x = ARRcookies[i].substr(0, ARRcookies[i].indexOf("="));
		y = ARRcookies[i].substr(ARRcookies[i].indexOf("=") + 1);
		x = x.replace(/^\s+|\s+$/g, "");
		if (x == c_name) {
			return unescape(y);
		}
	}
}

function setCookie(c_name, value, exdate) {
	var c_value = escape(value) + "; expires=" + exdate.toUTCString();
	document.cookie = c_name + "=" + c_value;
}

function setCookies() {
	username = document.forms["vote"]["user"].value;
	if (username == "") {
		alert("User cannot be empty");
		return false;
	}

	clientid = document.forms["vote"]["clientid"].value;


	var exdate = new Date();
	exdate.setDate(exdate.getDate() + 365); // year
	setCookie("username", username, exdate);
	
	exdate = new Date();
	exdate.setTime(exdate.getTime() + (8*60*60*1000)); // 8 hours
	setCookie("clientid", clientid, exdate);

	return true;
}

function checkCookie() {
	var username = getCookie("username");
	if (username != null && username != "") {
		var formobj = document.forms["vote"]
		if (formobj === undefined) {
			return;
		}
		var userObj = formobj["user"];
		userObj.value = username
	}
}

function checkJdu() {
	var checkObj = document.forms["vote"]["ucast"];
	if (checkObj.checked) {
		var divObj = document.getElementById("votesDiv");
		divObj.style.display = 'none';
	} else {
		var divObj = document.getElementById("votesDiv");
		divObj.style.display = 'block';
	}
}

function getUUID() {
	return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
		var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
		return v.toString(16);
	});
}

var entityMap = {
	"&" : "&amp;",
	"<" : "&lt;",
	">" : "&gt;",
	'"' : '&quot;',
	"'" : '&#39;',
	"/" : '&#x2F;'
};

function escapeHtml(string) {
	return String(string).replace(/[&<>"'\/]/g, function(s) {
		return entityMap[s];
	});
}

function log(msg) {
	setTimeout(function() {
		throw new Error(msg);
	}, 0);
}

// Channel related code
sendMessage = function(path, opt_param) {
	path += '?a=' + Date.now() // '?g=' + state.game_key;
	if (opt_param) {
		path += '&' + opt_param;
	}
	var xhr = new XMLHttpRequest();
	xhr.open('POST', path, true);
	xhr.send();
};

onOpened = function() {
	// Nothing to do
};

onChatMessage = function(m) {
	var el = $(m);
	var origVersion = $("#ajax_chat").text();
	var newVersion = $("#ajax_chat", el).text();
	if (origVersion == 'REFRESH_ME') {
		// console.log("CLEAR -> " + newVersion);
		$(".ajax_chat").html('<div id="ajax_chat"/>');
	}
	if (origVersion != newVersion) {
		// console.log("" + origVersion + "->" + newVersion);
		// console.log(el.text().replace(/\n+/g,"|"));

		$("#ajax_chat").replaceWith(m);
	}
}

onResultsMessage = function(m) {
	$("#ajax_results").replaceWith(m);
}

function sendVote() {
	if (!setCookies()) {
		return false;
	}

	var data = {}

	$(":input").each(
			function() {
				var form = $(this)[0].form;
				if (form.id !== "vote" || $(this)[0].name === undefined
						|| $(this)[0].name == '') {
					// console.log( "SKIPPING", $(this)[0].name,
					// $(this)[0].value, form.id );
				} else {
					// console.log( $(this)[0].name, $(this)[0].value, form.id
					// );

					if ($(this)[0].name === "ucast") {
						data[$(this)[0].name] = $(this)[0].checked ? "on"
								: "off";
					} else {
						data[$(this)[0].name] = $(this)[0].value;
					}
				}
			});

	// console.log(data);

	$.ajax({
		type : "POST",
		url : "results",
		// The key needs to match your method's input parameter
		// (case-sensitive).
		data : JSON.stringify(data),
		contentType : "application/json; charset=utf-8",
		dataType : "json",
		complete : function(data) {
			window.location.href = "results";
		},
		failure : function(msg) {
			alert(msg);
		}
	});

	return false;
}

function chngGroup(o) {
	$.ajax({
		type : "POST",
		url : "group",
		// The key needs to match your method's input parameter
		// (case-sensitive).
		data : JSON.stringify({ clientid: o.getAttribute("data-clientid"), groupid: o.getAttribute("data-groupid")}),
		contentType : "application/json; charset=utf-8",
		dataType : "json",
		complete : function(data) {
			window.location.href = "results";
		},
		failure : function(msg) {
			alert(msg);
		}
	});

	return false;
}

function sendEmptyChat() {
	var ajax_version = $("#ajax_chat").text();
	// sendMessage('/ajax_chat', "msg=" + escape('') + "&user=" +
	// unescape(getCookie('username')))
	$.post('ajax_chat', {
		msg : '',
		user : getCookie('username'),
		ajax_version : ajax_version
	}, onChatMessage);
	return false;
}

function sendChat() {
	var params = getSearchParameters();
	var msg = decodeURIComponent((document.forms["chat"]["msg"].value))
	var ajax_version = $("#ajax_chat").text();
	document.forms["chat"]["msg"].value = '';
	
	var dt = new Date();
	var time = String(dt.getHours()).padStart(2,'0') + ":" + String(dt.getMinutes()).padStart(2,'0') ;//+ ":" + String(dt.getSeconds()).padStart(2,'0');

	$.post('ajax_chat', {
		msg : msg,
		time, time,
		user : getCookie('username'),
		ajax_version : ajax_version
	}, onChatMessage);
	return false;
}

function onLoadFunc() {
	console.log("OnLoad")
	checkCookie();
	refreshAjax();
	poll();

	if ($("#ajax_results").length) {
		pollResults();
		// console.log("refreshResults");
		$.ajax({
			url : 'ajax_results',
			data : {},
			type : 'post',
			success : onResultsMessage,
			complete : pollResults
		});
	}
}

function refreshAjax() {
	// console.log("refreshAjax: " + $("#ajax_chat").text());
	sendEmptyChat();

	var $inputs = $('div.vote-slider.neutral');
	$inputs.each(function() {
		changeSlider($(this));
	});
}

$(document).ready(function() {
	onLoadFunc();

	// $(this).find('a').each(function(){
	// this.text('0');
	// });

});

function changeSlider(sl) {
	var v = sl.find('input')[0].valueAsNumber;

	var btn = sl.find('a')

	sl[0].classList.remove("v10");
	sl[0].classList.remove("v11");
	sl[0].classList.remove("v12");
	sl[0].classList.remove("v13");
	sl[0].classList.remove("v14");
	if (v === 10) {
		btn.text("--");
		sl[0].classList.add("v10");
	} else if (v === 11) {
		btn.text("0");
		sl[0].classList.add("v11");
	} else if (v === 12) {
		btn.text("+0");
		sl[0].classList.add("v12");
	} else if (v === 13) {
		btn.text("+");
		sl[0].classList.add("v13");
	} else if (v === 14) {
		btn.text("++");
		sl[0].classList.add("v14");
	}

	// console.log(sl[0].classList);
}

$(document).on('input change', '.vote-slider', function() {
	changeSlider($(this));
});

function hideDilbert() {
	document.getElementById("dilbert").style.display = "none";
}

function poll() {
	setTimeout(function() {
		var hour = new Date().getHours();
		if (hour < 10 || hour > 14) {
			return poll();
		}

		var ajax_version = $("#ajax_chat").text();
		// console.log("poll chat" + ajax_version);
		$.ajax({
			url : 'ajax_chat',
			data : {
				msg : '',
				user : getCookie('username'),
				ajax_version : ajax_version
			},
			type : 'post',
			success : onChatMessage,
			complete : poll
		});
	}, 15000);
};

function pollResults() {
	if ($("#ajax_results").length) {
		setTimeout(function() {
			var hour = new Date().getHours();
			if (hour < 10 || hour > 14) {
				return pollResults();
			}
			;

			$.ajax({
				url : 'ajax_results',
				data : {},
				type : 'post',
				success : onResultsMessage,
				complete : pollResults
			});
		}, 15000);
	}
};