/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aws.xray.demo.hitcounter.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.springframework.core.style.ToStringCreator;


@Entity
@Table(name = "site")
public class Site {

	@Id
	private String url;

	@Column(name = "description")
	private String description;

	@Column(name = "owner_first_name")
	private String ownerFirstName;

	@Column(name = "owner_last_name")
	private String ownerLastName;

	@Column(name = "hosted_country")
	private String hostedCountry;

	@Column(name = "category")
	private String category;

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getOwnerFirstName() {
		return ownerFirstName;
	}

	public void setOwnerFirstName(String ownerFirstName) {
		this.ownerFirstName = ownerFirstName;
	}

	public String getOwnerLastName() {
		return ownerLastName;
	}

	public void setOwnerLastName(String ownerLastName) {
		this.ownerLastName = ownerLastName;
	}

	public String getHostedCountry() {
		return hostedCountry;
	}

	public void setHostedCountry(String hostedCountry) {
		this.hostedCountry = hostedCountry;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	@Override
	public String toString() {
		return new ToStringCreator(this)
				.append("url", this.getUrl())
				.append("description", this.getDescription())
				.append("ownerFirstName", this.getOwnerFirstName())
				.append("ownerLastName", this.getOwnerLastName())
				.append("hostedCountry", this.getHostedCountry())
				.append("category", this.getCategory())
				.toString();
	}
}
