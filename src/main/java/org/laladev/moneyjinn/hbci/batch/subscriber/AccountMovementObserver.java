//
// Copyright (c) 2014-2015 Oliver Lehmann <lehmann@ans-netz.de>
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
// 1. Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer
// 2. Redistributions in binary form must reproduce the above copyright
// notice, this list of conditions and the following disclaimer in the
// documentation and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
// FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
// OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
// HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
// OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
// SUCH DAMAGE.
//
// $Id: AccountMovementObserver.java,v 1.7 2015/09/11 18:43:05 olivleh1 Exp $
//
package org.laladev.moneyjinn.hbci.batch.subscriber;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import org.laladev.moneyjinn.client.core.config.Configuration;
import org.laladev.moneyjinn.client.core.rest.util.MessageConverter;
import org.laladev.moneyjinn.client.core.rest.util.RequestInterceptor;
import org.laladev.moneyjinn.core.rest.model.ValidationResponse;
import org.laladev.moneyjinn.core.rest.model.importedmoneyflow.CreateImportedMoneyflowRequest;
import org.laladev.moneyjinn.core.rest.model.transport.ImportedMoneyflowTransport;
import org.laladev.moneyjinn.hbci.core.entity.AccountMovement;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

public class AccountMovementObserver implements Observer {

	private final RestTemplate restTemplate;

	public AccountMovementObserver() {
		this.restTemplate = new RestTemplate();

		this.restTemplate.getMessageConverters().clear();
		this.restTemplate.getMessageConverters().add(new MessageConverter());

		final List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
		interceptors.add(new RequestInterceptor());
		this.restTemplate.setInterceptors(interceptors);
	}

	@Override
	public void update(final Observable o, final Object arg) {
		if (arg instanceof AccountMovement) {
			this.notify((AccountMovement) arg);
		}

	}

	private void notify(final AccountMovement transaction) {
		final ImportedMoneyflowTransport transport = new ImportedMoneyflowTransport();
		transport.setAccountNumberCapitalsource(transaction.getMyIban());
		transport.setBankCodeCapitalsource(transaction.getMyBic());
		transport.setExternalid(transaction.getId().toString());
		transport.setBookingdate(Date.valueOf(transaction.getValueDate()));
		if (transaction.getInvoiceTimestamp() == null) {
			transport.setInvoicedate(Date.valueOf(transaction.getBookingDate()));
		} else {
			transport.setInvoicedate(Date.valueOf(transaction.getInvoiceTimestamp().toLocalDate()));
		}
		transport.setName(transaction.getOtherName());
		transport.setAccountNumber(transaction.getOtherAccountnumber() == null ? transaction.getOtherIban()
				: transaction.getOtherAccountnumber().toString());
		transport.setBankCode(transaction.getOtherBankcode() == null ? transaction.getOtherBic()
				: transaction.getOtherBankcode().toString());
		transport.setUsage(transaction.getMovementReason());
		transport.setAmount(transaction.getMovementValue());

		if (transport.getName() == null) {
			transport.setName(" ");
		}

		if (transport.getAccountNumber() == null) {
			transport.setAccountNumber(" ");
		}

		if (transport.getBankCode() == null) {
			transport.setBankCode(" ");
		}

		final CreateImportedMoneyflowRequest request = new CreateImportedMoneyflowRequest();
		request.setImportedMoneyflowTransport(transport);

		final ValidationResponse response = this.restTemplate.postForObject(
				Configuration.ROOT_URL + "/importedmoneyflow/createImportedMoneyflow", request,
				ValidationResponse.class);

		if (response != null) {
			if (response.getErrorResponse() != null) {
				throw (new RuntimeException("error: " + response.getErrorResponse().getMessage()));
			} else if (response.getResult().equals(Boolean.FALSE)) {
				throw (new RuntimeException(
						"error: " + response.getValidationItemTransports().get(0).getError().toString()));
			}

		}
	}

}
