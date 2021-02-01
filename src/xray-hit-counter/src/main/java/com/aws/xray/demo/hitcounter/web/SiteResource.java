package com.aws.xray.demo.hitcounter.web;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.spring.aop.XRayEnabled;
import com.aws.xray.demo.hitcounter.dynamodb.HitRecorder;
import com.aws.xray.demo.hitcounter.model.Site;
import com.aws.xray.demo.hitcounter.model.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/sites")
@RestController
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
@XRayEnabled
public class SiteResource {

	private final SiteRepository siteRepository;

	@Autowired
	private HitRecorder hitRecorder;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public ResponseEntity<Site> createSite(@RequestBody Site site) {
		final String siteUrl = site.getUrl();
		ResponseEntity<Site> responseEntity;
		String error;

		// Fetch Subsegment
		Subsegment fetchSubSegment = AWSXRay.beginSubsegment("Lookup site by URL");
		final Optional<Site> siteOptional = siteRepository.findById(site.getUrl());
		fetchSubSegment.setAnnotations(Map.of("isPresent", siteOptional.isPresent(), "url", siteUrl));

		if (siteOptional.isPresent()) {
			error = String.format("URL already exists %s", siteUrl);
			fetchSubSegment.addException(new Exception(error));
			responseEntity = new ResponseEntity<>(HttpStatus.BAD_REQUEST);
			AWSXRay.endSubsegment();
		}
		else {
			AWSXRay.endSubsegment();

			// Save subsegment
			Subsegment saveSubSegment = AWSXRay.beginSubsegment("Save site details");
			final Site newSite = siteRepository.save(site);
			log.info("New site created {}", newSite.getUrl());
			responseEntity = new ResponseEntity<>(siteRepository.save(site), HttpStatus.OK);
			saveSubSegment.setAnnotations(Map.of("siteUrl", newSite.getUrl()));
			AWSXRay.endSubsegment();

			Subsegment dynamodbSubSegment = AWSXRay.beginSubsegment("Add to " + siteUrl + " to dynamodb");
			Throwable dynamodbError = hitRecorder.createHitEntry(siteUrl);
			if (dynamodbError != null) {
				error = "Error while saving entries to dynamodb " + dynamodbError.getMessage();
				log.error(error, dynamodbError);
				siteRepository.delete(newSite);
				dynamodbSubSegment.addException(dynamodbError);
			}
			AWSXRay.endSubsegment();
		}

		return responseEntity;
	}


	@GetMapping(value = "/details")
	@Transactional(readOnly = true)
	public ResponseEntity<Site> getSiteDetails(@RequestParam("url") String url) {
		Optional<Site> site = siteRepository.findById(url);
		if (site.isEmpty()) {
			String error = String.format("URL doesnt exists %s", url);
			log.error(error);
			AWSXRay.getCurrentSegment().addException(new Exception(error));
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		else
			return new ResponseEntity<>(site.get(), HttpStatus.OK);
	}


	@GetMapping
	@Transactional(readOnly = true)
	public ResponseEntity<List<Site>> findAll() {
		List<Site> siteList = siteRepository.findAll();
		AWSXRay.getCurrentSegment().setAnnotations(Map.of("Total site count", siteList.size()));
		return new ResponseEntity<>(siteList, HttpStatus.OK);
	}

	@Transactional
	@DeleteMapping
	public void deleteSite(@RequestParam("url") String url){
		siteRepository.delete(siteRepository.getOne(url));
	}

	@PutMapping("/hits")
	@ResponseStatus(HttpStatus.CREATED)
	public void recordHit(@RequestParam("url") String url) {
		hitRecorder.saveHit(url);
	}

	@GetMapping("/hits")
	public ResponseEntity<Integer> getHits(@RequestParam("url") String url) {
		return new ResponseEntity<>(hitRecorder.getHitCount(url), HttpStatus.OK);
	}
}
